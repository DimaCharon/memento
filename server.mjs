/**
 * Memento — продакшн-сервер (RelaxDev / любой Node-хостинг).
 *
 * Что делает:
 *  1. Отдаёт собранный фронтенд из dist/ (SPA: любой путь → index.html).
 *  2. Обслуживает внутренний шлюз агентов: /api/health, /api/claude, /api/outbound.
 *  3. Слушает 0.0.0.0 и порт из окружения (RelaxDev даёт PORT=8080).
 *
 * Важно для RelaxDev: переменные читаются через квадратные скобки
 * (process.env['ИМЯ']). Сканер заглушек платформы такие обращения не подменяет,
 * поэтому значения по умолчанию работают, а не превращаются в
 * "auto-generated-stub-for-build".
 */
import http from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('.', import.meta.url))
const DIST = join(ROOT, 'dist')

const readEnv = (name) => process.env[name]
const STUB_MARK = 'auto-generated-stub'

const PORT = Number(readEnv('PORT') || readEnv('SERVER_PORT') || 8080)
const HOST = readEnv('HOST') || '0.0.0.0'
const CLAUDE_MODEL = readEnv('ANTHROPIC_MODEL') || 'claude-sonnet-4-5'
const PROXY_URL = readEnv('OUTBOUND_PROXY_URL') || readEnv('RELAXDEV_PROXY_URL') || ''

/** Значение пригодно к использованию (не пустое и не заглушка сборки). */
function real(value) {
  return Boolean(value) && !String(value).startsWith(STUB_MARK)
}

/** Исходящий прокси платформы — только если он реально задан в панели. */
let dispatcher
if (real(PROXY_URL)) {
  try {
    const { ProxyAgent } = await import('undici')
    dispatcher = new ProxyAgent(PROXY_URL)
    console.log('[memento] исходящие запросы идут через прокси платформы')
  } catch (e) {
    console.warn('[memento] undici недоступен, прокси не подключён:', e?.message)
  }
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.ics': 'text/calendar; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
}

function sendJson(res, code, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(body),
  })
  res.end(body)
}

function readBody(req, limit = 512 * 1024) {
  return new Promise((resolve) => {
    let raw = ''
    let size = 0
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > limit) {
        req.destroy()
        resolve({})
        return
      }
      raw += chunk
    })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
    req.on('error', () => resolve({}))
  })
}

/** Защита от SSRF: не выпускаем прокси-запросы внутрь инфраструктуры. */
function isPublicUrl(raw) {
  try {
    const url = new URL(raw)
    if (!/^https?:$/.test(url.protocol)) return false
    const host = url.hostname.toLowerCase()
    if (host === 'localhost' || host.endsWith('.local') || host.endsWith('.internal')) return false
    if (host === '::1' || host === '0.0.0.0') return false
    if (/^127\./.test(host) || /^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host)) return false
    if (/^172\.(1[6-9]|2\d|3[01])\./.test(host)) return false
    if (/^\[?fd[0-9a-f]{2}:/i.test(host) || /^\[?fe80:/i.test(host)) return false
    return true
  } catch {
    return false
  }
}

async function outboundFetch(url, options = {}) {
  const opts = { ...options }
  if (dispatcher) opts.dispatcher = dispatcher
  return fetch(url, opts)
}

/* ----------------------------- Внутренний шлюз ---------------------------- */

async function apiClaude(body) {
  const key = readEnv('ANTHROPIC_API_KEY')
  if (!real(key)) {
    return [200, {
      fallback: true,
      reason: 'ANTHROPIC_API_KEY не задан в переменных проекта — агенты работают на встроенном локальном движке.',
    }]
  }
  try {
    const r = await outboundFetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': key,
        'anthropic-version': '2023-06-01',
      },
      body: JSON.stringify({
        model: CLAUDE_MODEL,
        max_tokens: 1024,
        system: body?.system || 'Ты агент Memento. Отвечай кратко по-русски.',
        messages: [{ role: 'user', content: String(body?.prompt || '') }],
      }),
    })
    const data = await r.json().catch(() => ({}))
    if (!r.ok) {
      return [502, { error: data?.error?.message || `Claude API ответил ${r.status}` }]
    }
    const text = (data.content || []).map((p) => p.text || '').join('').trim()
    return [200, { text, model: data.model, usage: data.usage }]
  } catch (e) {
    return [502, { error: String(e?.message || e) }]
  }
}

async function apiOutbound(body) {
  const { url, method = 'POST', body: payload, headers = {} } = body || {}
  if (!isPublicUrl(url)) return [400, { error: 'Некорректный или внутренний URL' }]
  try {
    const r = await outboundFetch(url, {
      method,
      headers: { 'content-type': 'application/json', ...headers },
      body: method === 'GET' ? undefined : JSON.stringify(payload ?? {}),
    })
    const text = (await r.text()).slice(0, 2000)
    return [200, { status: r.status, ok: r.ok, body: text }]
  } catch (e) {
    return [502, { error: String(e?.message || e) }]
  }
}

/* --------------------------------- Статика -------------------------------- */

function serveStatic(req, res, pathname) {
  const safe = normalize(decodeURIComponent(pathname)).replace(/^(\.\.[/\\])+/, '')
  const target = join(DIST, safe)

  if (existsSync(target) && statSync(target).isFile()) {
    const ext = extname(target).toLowerCase()
    const immutable = target.includes(`${join(DIST, 'assets')}`)
    res.writeHead(200, {
      'content-type': MIME[ext] || 'application/octet-stream',
      'cache-control': immutable ? 'public, max-age=31536000, immutable' : 'no-cache',
    })
    createReadStream(target).pipe(res)
    return
  }

  // SPA: любой неизвестный путь отдаёт index.html
  const index = join(DIST, 'index.html')
  if (!existsSync(index)) {
    res.writeHead(503, { 'content-type': 'text/plain; charset=utf-8' })
    res.end('Сборка не найдена. Выполните npm run build перед запуском сервера.')
    return
  }
  res.writeHead(200, { 'content-type': MIME['.html'], 'cache-control': 'no-cache' })
  createReadStream(index).pipe(res)
}

/* ---------------------------------- Сервер -------------------------------- */

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  const path = url.pathname

  if (path.startsWith('/api/')) {
    if (path === '/api/health') {
      return sendJson(res, 200, {
        ok: true,
        app: 'memento',
        claudeKey: real(readEnv('ANTHROPIC_API_KEY')),
        proxy: Boolean(dispatcher),
        node: process.version,
        uptime: Math.round(process.uptime()),
      })
    }
    if (req.method !== 'POST') return sendJson(res, 405, { error: 'Нужен POST' })
    const body = await readBody(req)
    const [code, payload] = path === '/api/claude'
      ? await apiClaude(body)
      : path === '/api/outbound'
        ? await apiOutbound(body)
        : [404, { error: 'Неизвестный маршрут' }]
    return sendJson(res, code, payload)
  }

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { 'content-type': 'text/plain; charset=utf-8' })
    return res.end('Method Not Allowed')
  }

  serveStatic(req, res, path)
})

server.listen(PORT, HOST, () => {
  console.log(`[memento] сервер запущен: http://${HOST}:${PORT}`)
  console.log(`[memento] Claude API: ${real(readEnv('ANTHROPIC_API_KEY')) ? 'ключ задан' : 'локальный движок агентов'}`)
})

for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    console.log(`[memento] получен ${signal}, останавливаюсь`)
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 5000)
  })
}
