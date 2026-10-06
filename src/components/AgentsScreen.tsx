import { useEffect, useRef, useState } from 'react'
import { Icon } from './Icon'
import { useStore } from '../store'
import { AGENTS, TOOLS } from '../agent/agents'

/** Пример фразы для реального вызова инструмента из списка MCP-шины. */
function sampleFor(tool: string) {
  switch (tool) {
    case 'create_reminder': return 'Напомни завтра в 10:00 проверить инструмент create_reminder'
    case 'update_reminder': return 'Напомни через 45 минут проверить обновление задачи'
    case 'complete_reminder': return 'Отметь выполненным проверить обновление задачи'
    case 'snooze_reminder': return 'Отложи проверить обновление задачи на 10 минут'
    case 'delete_reminder': return 'Удали напоминание проверить обновление задачи'
    case 'query_reminders': return 'Покажи все напоминания'
    case 'set_widget_layout': return 'Поставь виджет 4×4'
    case 'set_setting': return 'Включи звук'
    case 'navigate': return 'Открой календарь'
    default: return 'Покажи все напоминания'
  }
}
import { fmtClock } from '../lib/date'

const QUICK = [
  'Напомни завтра в 9:00 встреча с командой',
  'Что у меня на сегодня?',
  'Отложи тренировку на 30 минут',
  'Поставь виджет 4×4',
  'Проверь всё',
]

export function AgentsScreen() {
  const { account, ask, thinking, agentStatus, runTask, toast } = useStore()
  const [text, setText] = useState('')
  const [tab, setTab] = useState<'chat' | 'pipeline' | 'tools'>('chat')
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (tab === 'chat' && scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight
  }, [account.chat.length, thinking, tab])

  const send = async (value?: string) => {
    const v = (value ?? text).trim()
    if (!v) return
    setText('')
    if (v.toLowerCase().includes('проверь всё')) {
      await runTask('audit')
      return
    }
    await ask(v)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div className="page-head" style={{ paddingBottom: 8 }}>
        <div>
          <div className="h">Агенты</div>
          <div className="hint">Пайплайн из 5 агентов · Claude Sonnet 5</div>
        </div>
        <span className={`pill ${thinking ? 'run' : 'ok'}`}>
          {thinking ? <><i className="pulse" />работаю</> : <><Icon name="check" size={11} />готов</>}
        </span>
      </div>

      <div className="seg" style={{ marginBottom: 10 }}>
        <button className={tab === 'chat' ? 'on' : ''} onClick={() => setTab('chat')}>Диалог</button>
        <button className={tab === 'pipeline' ? 'on' : ''} onClick={() => setTab('pipeline')}>Пайплайн</button>
        <button className={tab === 'tools' ? 'on' : ''} onClick={() => setTab('tools')}>Инструменты</button>
      </div>

      {tab === 'chat' ? (
        <>
          <div className="phone-scroll" ref={scrollRef} style={{ padding: '0 0 112px', flex: 1 }}>
            <div className="chat-scroll">
              {account.chat.map((m) => (
                <div key={m.id} className={`bubble ${m.role}`}>
                  {m.role !== 'system' && m.agent ? <div className="who"><Icon name="sparkles" size={11} />{m.agent}</div> : null}
                  {m.text}
                  {m.logIds?.length ? <div className="why">Проверено по {m.logIds.length} шагам журнала</div> : null}
                </div>
              ))}
              {thinking ? (
                <div className="bubble agent">
                  <div className="who"><Icon name="sparkles" size={11} />Пайплайн работает</div>
                  <span className="typing">парсер → планировщик → редактор → валидатор <i /><i /><i /></span>
                </div>
              ) : null}
            </div>
          </div>
          <div className="composer-dock">
          <div className="chips chips-row" style={{ padding: '8px 0 4px' }}>
            {QUICK.map((q) => (
              <button key={q} className="chip" onClick={() => send(q)} disabled={thinking}>{q}</button>
            ))}
          </div>
          <div className="composer">
            <div className="field">
              <span className="f-ico"><Icon name="edit" size={17} /></span>
              <textarea
                rows={1}
                value={text}
                placeholder="Напиши фразой: «напомни в пятницу в 19:30 ужин с семьёй»"
                onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    send()
                  }
                }}
              />
            </div>
            <button className="send-btn" onClick={() => send()} disabled={thinking} aria-label="Отправить">
              <Icon name="send" size={18} />
            </button>
          </div>
          </div>
        </>
      ) : null}

      {tab === 'pipeline' ? (
        <div className="phone-scroll" style={{ padding: 0, flex: 1 }}>
          {AGENTS.map((a) => {
            const st = agentStatus[a.id] || 'idle'
            const label = st === 'run' ? 'работает' : st === 'ok' ? 'успех' : st === 'warn' ? 'предупреждение' : st === 'err' ? 'ошибка' : 'ожидает'
            return (
              <div key={a.id} className={`agent-card${a.chief ? ' chief' : ''}`}>
                <div className="agent-ico"><Icon name={a.icon} size={18} /></div>
                <div style={{ flex: 1 }}>
                  <div className="agent-name">{a.name}</div>
                  <div className="agent-role">{a.role}</div>
                  <div className="agent-model">
                    <Icon name="sparkles" size={11} /> {a.model}
                    {a.chief ? <span className="pill" style={{ marginLeft: 4 }}>главный · контроль качества</span> : null}
                  </div>
                </div>
                <span className={`pill ${st}`}>
                  {st === 'run' ? <i className="pulse" /> : st === 'ok' ? <Icon name="check" size={11} /> : null}
                  {label}
                </span>
              </div>
            )
          })}

          <div className="section-title">Журнал выполнения</div>
          <div className="panel" style={{ maxHeight: 300, overflowY: 'auto' }}>
            {account.logs.length ? [...account.logs].reverse().map((l) => (
              <div key={l.id} className="log-item">
                <span className={`log-dot ${l.status}`} />
                <div style={{ flex: 1 }}>
                  <div className="log-agent">{fmtClock(new Date(l.at))} · {l.agent} · <span className="mono">{l.model}</span></div>
                  <div className="log-text">{l.text}</div>
                </div>
              </div>
            )) : <div className="hint">Журнал пуст — дай агенту задачу.</div>}
          </div>

          <div className="section-title">Автономные задачи</div>
          <div className="btn-row" style={{ flexWrap: 'wrap' }}>
            <button className="btn ghost sm" onClick={() => runTask('audit')} disabled={thinking}><Icon name="shield" size={14} /> Аудит расписания</button>
            <button className="btn ghost sm" onClick={() => runTask('reschedule')} disabled={thinking}><Icon name="chart" size={14} /> Разгрузить вечер</button>
            <button className="btn ghost sm" onClick={() => runTask('demo')} disabled={thinking}><Icon name="play" size={14} /> Демо-сценарий</button>
            <button
              className="btn ghost sm"
              onClick={() => {
                setTab('chat')
                setTimeout(() => document.querySelector<HTMLTextAreaElement>('.composer textarea')?.focus(), 240)
                toast('Опиши задачу фразой — агент разберёт её сам', 'info')
              }}
              disabled={thinking}
            >
              <Icon name="sparkles" size={14} /> Своя задача
            </button>
          </div>
          <div className="hint" style={{ marginTop: 8 }}>
            Главный агент Orchestrator сам решает, каких агентов звать, а результат проверяет валидатор — он читает базу заново и сверяет каждую запись.
          </div>
          <div style={{ height: 12 }} />
        </div>
      ) : null}

      {tab === 'tools' ? (
        <div className="phone-scroll" style={{ padding: 0, flex: 1 }}>
          <div className="hint" style={{ marginBottom: 10 }}>
            Контракт инструментов, которые агенты вызывают в приложении (совместим с MCP-шиной Memento). Каждый вызов виден в журнале и подтверждается валидатором.
          </div>
          {TOOLS.map((t) => (
            <div key={t.name} className="panel" style={{ marginBottom: 9 }}>
              <div className="row" style={{ marginBottom: 6 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Icon name="code" size={15} style={{ color: 'var(--accent-strong)' }} />
                  <span className="mono">{t.name}</span>
                </div>
                <button
                  className="chip"
                  onClick={async () => {
                    setTab('chat')
                    await ask(sampleFor(t.name))
                  }}
                  disabled={thinking}
                  title="Прогнать реальный вызов инструмента через агентов"
                >
                  <Icon name="play" size={11} /> тест
                </button>
              </div>
              <div className="hint">{t.desc}</div>
              <div className="chips" style={{ marginTop: 8 }}>
                {Object.entries(t.schema).map(([k, v]) => (
                  <span key={k} className="chip mono" style={{ fontSize: 10.5 }}>{k}: {v}</span>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}
