import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import type { IncomingMessage, ServerResponse } from 'node:http'

/**
 * Локальный шлюз для коннекторов.
 *  POST /api/claude   — опциональный реальный вызов Claude API (если задан ANTHROPIC_API_KEY).
 *                       Без ключа отвечает { fallback: true } и приложение использует локальный движок агентов.
 *  POST /api/outbound — прокси для исходящих тестов коннекторов (Telegram / Webhook),
 *                       чтобы не упираться в CORS-ограничения браузера.
 */
function readBody(req: IncomingMessage): Promise<any> {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (c: Buffer) => (raw += c))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

function json(res: ServerResponse, code: number, payload: unknown) {
  res.statusCode = code
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify(payload))
}

async function handle(path: string, body: any): Promise<[number, unknown]> {
  if (path === '/api/claude') {
    const key = process.env['ANTHROPIC_API_KEY']
    if (!key) {
      return [200, { fallback: true, reason: 'ANTHROPIC_API_KEY не задан — работает локальный движок агентов.' }]
    }
    try {
      const r = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-api-key': key,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: process.env['ANTHROPIC_MODEL'] || 'claude-sonnet-4-5',
          max_tokens: 1024,
          system: body.system,
          messages: [{ role: 'user', content: String(body.prompt || '') }],
        }),
      })
      const data: any = await r.json()
      if (!r.ok) return [502, { error: data?.error?.message || 'Ошибка Claude API' }]
      const text = (data.content || []).map((p: any) => p.text || '').join('').trim()
      return [200, { text, model: data.model, usage: data.usage }]
    } catch (e: any) {
      return [502, { error: String(e?.message || e) }]
    }
  }

  if (path === '/api/outbound') {
    const { url, method = 'POST', body: payload, headers = {} } = body || {}
    if (!url || !/^https?:\/\//.test(url)) return [400, { error: 'Некорректный URL' }]
    try {
      const r = await fetch(url, {
        method,
        headers: { 'content-type': 'application/json', ...headers },
        body: method === 'GET' ? undefined : JSON.stringify(payload ?? {}),
      })
      const text = (await r.text()).slice(0, 2000)
      return [200, { status: r.status, ok: r.ok, body: text }]
    } catch (e: any) {
      return [502, { error: String(e?.message || e) }]
    }
  }

  if (path === '/api/health') {
    return [200, { ok: true, claudeKey: Boolean(process.env['ANTHROPIC_API_KEY']) }]
  }

  return [404, { error: 'Неизвестный маршрут' }]
}

function gateway(): Plugin {
  const middleware = async (req: IncomingMessage, res: ServerResponse, next: (e?: any) => void) => {
    const url = req.url || ''
    if (!url.startsWith('/api/')) return next()
    const path = url.split('?')[0]
    const body = req.method === 'POST' ? await readBody(req) : {}
    const [code, payload] = await handle(path, body)
    json(res, code, payload)
  }
  return {
    name: 'memento-gateway',
    configureServer(server) {
      server.middlewares.use((req, res, next) => void middleware(req, res, next))
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => void middleware(req, res, next))
    },
  }
}

export default defineConfig({
  plugins: [react(), gateway()],
  server: { host: '0.0.0.0', port: 5173, strictPort: false, allowedHosts: true },
  preview: { host: '0.0.0.0', port: 4173, strictPort: false, allowedHosts: true },
})
