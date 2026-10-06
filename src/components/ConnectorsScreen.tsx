import { useState } from 'react'
import { Icon } from './Icon'
import { Modal } from './Sheet'
import { useStore } from '../store'
import { TOOLS } from '../agent/agents'
import type { Connector } from '../types'
import { dayLabel, fullDateLabel, repeatLabel, timeToMinutes } from '../lib/date'

function download(name: string, content: string, type = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function ConnectorsScreen() {
  const { account, setConnector, setConnectorField, toast } = useStore()
  const [busy, setBusy] = useState<string | null>(null)
  const [openTools, setOpenTools] = useState(false)

  const reminders = account.reminders

  const buildICS = () => {
    const pad = (n: number) => String(n).padStart(2, '0')
    const stamp = (d: Date) => `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}T${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}00Z`
    const lines = [
      'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Memento//RU', 'CALSCALE:GREGORIAN',
      ...reminders.map((r) => {
        const [y, m, d] = r.date.split('-').map(Number)
        const [hh, mm] = r.time.split(':').map(Number)
        const start = new Date(y, m - 1, d, hh, mm)
        const end = new Date(start.getTime() + 30 * 60000)
        return [
          'BEGIN:VEVENT',
          `UID:${r.id}@memento.app`,
          `DTSTAMP:${stamp(new Date())}`,
          `DTSTART:${stamp(start)}`,
          `DTEND:${stamp(end)}`,
          `SUMMARY:${r.title.replace(/[,;]/g, '')}`,
          r.notes ? `DESCRIPTION:${r.notes.replace(/\n/g, ' ').replace(/[,;]/g, '')}` : '',
          r.place ? `LOCATION:${r.place}` : '',
          r.repeat === 'daily' ? 'RRULE:FREQ=DAILY' : r.repeat === 'weekly' ? 'RRULE:FREQ=WEEKLY' : r.repeat === 'weekdays' ? 'RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR' : r.repeat === 'monthly' ? 'RRULE:FREQ=MONTHLY' : '',
          r.leadMinutes ? `BEGIN:VALARM\nTRIGGER:-PT${r.leadMinutes}M\nACTION:DISPLAY\nDESCRIPTION:${r.title}\nEND:VALARM` : '',
          'END:VEVENT',
        ].filter(Boolean).join('\r\n')
      }),
      'END:VCALENDAR',
    ]
    return lines.join('\r\n')
  }

  const buildMarkdown = () =>
    [
      `# Memento — напоминания (${fullDateLabel(new Date().toISOString().slice(0, 10))})`,
      '',
      ...reminders
        .slice()
        .sort((a, b) => a.date.localeCompare(b.date) || timeToMinutes(a.time) - timeToMinutes(b.time))
        .map((r) => `- [${r.done ? 'x' : ' '}] **${r.time}** · ${r.title}${r.place ? ` _(${r.place})_` : ''}${r.repeat !== 'none' ? ` 🔁 ${repeatLabel(r.repeat)}` : ''}${r.notes ? `\n      ${r.notes}` : ''}`),
      '',
      `_Выгружено из Memento · всего ${reminders.length} записей_`,
    ].join('\n')

  const test = async (c: Connector) => {
    setBusy(c.id)
    try {
      if (c.test === 'ics') {
        download('memento-reminders.ics', buildICS(), 'text/calendar;charset=utf-8')
        toast('Файл .ics выгружен — открой его в календаре', 'ok')
      } else if (c.test === 'markdown') {
        download('memento-reminders.md', buildMarkdown(), 'text/markdown;charset=utf-8')
        toast('Markdown-выгрузка готова', 'ok')
      } else if (c.test === 'mcp') {
        setOpenTools(true)
      } else if (c.test === 'browserNotify') {
        if (!('Notification' in window)) {
          toast('Браузер не поддерживает уведомления', 'err')
        } else {
          const perm = await Notification.requestPermission()
          if (perm === 'granted') {
            setConnector(c.id, { connected: true })
            new Notification('Memento подключён', { body: 'Уведомления будут приходить, даже если вкладка свёрнута.' })
            toast('Разрешение получено', 'ok')
          } else {
            toast('Браузер отклонил запрос уведомлений', 'warn')
          }
        }
      } else if (c.test === 'claude') {
        const res = await fetch('/api/claude', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            system: 'Ты агент Memento. Ответь одной короткой фразой по-русски.',
            prompt: 'Проверка связи: подтверди, что готов планировать напоминания.',
          }),
        })
        const data = await res.json()
        if (data.fallback) toast(data.reason, 'warn')
        else if (data.error) toast(`Claude API: ${data.error}`, 'err')
        else toast(`Claude ответил: ${String(data.text).slice(0, 90)}`, 'ok')
      } else if (c.test === 'telegram') {
        const token = c.fields.find((f) => f.key === 'token')?.value
        const chatId = c.fields.find((f) => f.key === 'chatId')?.value
        if (!token || !chatId) {
          toast('Заполни токен бота и Chat ID', 'warn')
        } else {
          const res = await fetch('/api/outbound', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
              url: `https://api.telegram.org/bot${token}/sendMessage`,
              body: { chat_id: chatId, text: 'Memento подключён ✅ Тестовое сообщение' },
            }),
          })
          const data = await res.json()
          toast(data.ok ? `Telegram принял сообщение (${data.status})` : `Telegram ответил ошибкой ${data.status ?? data.error}`, data.ok ? 'ok' : 'err')
        }
      } else if (c.test === 'webhook') {
        const url = c.fields.find((f) => f.key === 'url')?.value
        if (!url) toast('Укажи URL вебхука', 'warn')
        else {
          const res = await fetch('/api/outbound', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
              url,
              body: { event: 'test', app: 'Memento', reminders: reminders.length, at: new Date().toISOString() },
            }),
          })
          const data = await res.json()
          toast(data.ok ? `Webhook ответил ${data.status}` : `Ошибка: ${data.status ?? data.error}`, data.ok ? 'ok' : 'err')
        }
      }
    } catch (e: any) {
      toast(`Не удалось: ${e?.message || e}`, 'err')
    } finally {
      setBusy(null)
    }
  }

  const toggle = async (c: Connector) => {
    if (c.test === 'browserNotify' && !c.connected) {
      await test(c)
      return
    }
    const required = c.fields.filter((f) => !f.value.trim())
    if (!c.connected && required.length) {
      toast(`Сначала заполни: ${required.map((f) => f.label).join(', ')}`, 'warn')
      return
    }
    setConnector(c.id, { connected: !c.connected })
    toast(`${c.name}: ${c.connected ? 'отключено' : 'подключено'}`, 'ok')
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="h">Коннекторы</div>
          <div className="hint">Мосты между Memento и внешними сервисами</div>
        </div>
      </div>

      {account.connectors.map((c) => (
        <div key={c.id} className="panel" style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', gap: 11, alignItems: 'flex-start' }}>
            <div className="agent-ico"><Icon name={c.icon} size={17} /></div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13.5, fontWeight: 600 }}>{c.name}</div>
              <div className="hint" style={{ marginTop: 3 }}>{c.desc}</div>
            </div>
            <button className={`switch${c.connected ? ' on' : ''}`} onClick={() => toggle(c)} aria-pressed={c.connected} />
          </div>

          {c.fields.length ? (
            <div style={{ marginTop: 10 }}>
              {c.fields.map((f) => (
                <div key={f.key} className="field" style={{ marginTop: 7 }}>
                  <span className="f-ico"><Icon name={f.type === 'password' ? 'lock' : 'edit'} size={16} /></span>
                  <div className="f-body">
                    <div className="f-label">{f.label}</div>
                    <input
                      type={f.type === 'password' ? 'password' : 'text'}
                      value={f.value}
                      placeholder={f.placeholder}
                      onChange={(e) => setConnectorField(c.id, f.key, e.target.value)}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : null}

          <div className="btn-row" style={{ marginTop: 10, flexWrap: 'wrap' }}>
            <button className="btn ghost sm" onClick={() => test(c)} disabled={busy === c.id}>
              {busy === c.id ? <i className="pulse" /> : <Icon name="play" size={14} />}
              {c.test === 'ics' ? 'Выгрузить .ics'
                : c.test === 'markdown' ? 'Выгрузить .md'
                  : c.test === 'mcp' ? 'Показать инструменты'
                    : c.test === 'browserNotify' ? 'Запросить доступ'
                      : c.test === 'claude' ? 'Проверить ключ'
                        : 'Тест-запрос'}
            </button>
            <button className="btn ghost sm" onClick={() => toggle(c)}>
              {c.connected ? 'Отключить' : 'Подключить'}
            </button>
            <span className={`pill ${c.connected ? 'ok' : ''}`} style={{ marginLeft: 'auto' }}>
              <Icon name={c.connected ? 'check' : 'link'} size={11} />
              {c.connected ? 'подключено' : 'не подключено'}
            </span>
          </div>
        </div>
      ))}

      <div className="panel">
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <Icon name="database" size={17} style={{ color: 'var(--accent-strong)', marginTop: 2 }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>Как это работает</div>
            <div className="hint" style={{ marginTop: 4 }}>
              ИИ-агенты не имеют прямого доступа к данным: они вызывают инструменты MCP-шины, а валидатор подтверждает
              каждый вызов. Ключи хранятся только в localStorage твоего браузера; тест-запросы идут через локальный
              шлюз приложения, чтобы не рушить CORS.
            </div>
          </div>
        </div>
      </div>

      {openTools ? (
        <Modal
          title="Memento MCP-шина"
          subtitle="Инструменты, которые агенты вызывают в приложении. Тот же контракт можно отдать внешнему MCP-клиенту."
          onClose={() => setOpenTools(false)}
          wide
        >
          {TOOLS.map((t) => (
            <div key={t.name} className="panel" style={{ marginBottom: 9 }}>
              <div className="mono" style={{ color: 'var(--accent-strong)' }}>{t.name}</div>
              <div className="hint" style={{ marginTop: 4 }}>{t.desc}</div>
              <div className="chips" style={{ marginTop: 8 }}>
                {Object.entries(t.schema).map(([k, v]) => (
                  <span key={k} className="chip mono" style={{ fontSize: 10.5 }}>{k}: {v}</span>
                ))}
              </div>
            </div>
          ))}
        </Modal>
      ) : null}
    </>
  )
}
