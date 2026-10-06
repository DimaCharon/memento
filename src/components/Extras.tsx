import { Icon } from './Icon'
import { useStore } from '../store'
import { AGENTS, DEFAULT_WIDGETS, TOOLS } from '../agent/agents'
import { fmtClock, fmtLongDate, fmtSeconds, relativeLabel, reminderDate, timeToMinutes, todayISO, plural } from '../lib/date'

export function Toasts() {
  const { toasts } = useStore()
  return (
    <div className="toasts">
      {toasts.map((t) => (
        <div key={t.id} className={`toast-item ${t.kind}`}>{t.text}</div>
      ))}
    </div>
  )
}

/* ------------------------------ Экран блокировки --------------------------- */

export function LockScreen() {
  const { account, now, unlock, snooze, toggleDone } = useStore()
  const active = account.reminders
    .filter((r) => !r.done)
    .sort((a, b) => (a.snoozedUntil ?? reminderDate(a)) - (b.snoozedUntil ?? reminderDate(b)))
  const overdue = active.filter((r) => (r.snoozedUntil ?? reminderDate(r)) <= now)
  const feed = [...overdue, ...active.filter((r) => !overdue.includes(r))].slice(0, 4)

  return (
    <div className="lock-screen">
      <div className="lock-top">
        <div className="lock-lock"><Icon name="lock" size={20} /></div>
        <div className="lock-time">
          {fmtClock(new Date(now))}
          <span style={{ fontSize: 22, color: 'var(--txt-3)', marginLeft: 4, verticalAlign: 'super' }}>{fmtSeconds(new Date(now))}</span>
        </div>
        <div className="lock-date">{fmtLongDate(new Date(now))}</div>
      </div>

      <div className="lock-list">
        {feed.map((r) => {
          const due = (r.snoozedUntil ?? reminderDate(r)) <= now
          return (
            <div key={r.id} className="notif unread" style={{ marginBottom: 10 }}>
              <div className="agent-ico" style={{ width: 38, height: 38 }}><Icon name={r.icon} size={17} /></div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="nm">Memento · {due ? 'сейчас' : relativeLabel(r.snoozedUntil ?? reminderDate(r), now)}</div>
                <div className="body" style={{ fontSize: 14, fontWeight: 600, color: 'var(--txt)' }}>{r.title}</div>
                <div className="tm">
                  <Icon name="clock" size={12} />
                  {due ? 'Пора выполнить' : `Начало в ${r.time}`}
                  {r.place ? ` · ${r.place}` : ''}
                </div>
                <div className="btn-row" style={{ marginTop: 9 }}>
                  <button className="btn soft sm" style={{ flex: 1 }} onClick={() => snooze(r.id, 10)}>Отложить</button>
                  <button className="btn soft sm" style={{ flex: 1 }} onClick={() => toggleDone(r.id)}>Выполнено</button>
                </div>
              </div>
            </div>
          )
        })}
        {!feed.length ? <div className="empty"><Icon name="check" size={22} />Активных напоминаний нет</div> : null}
      </div>

      <div className="lock-bottom">
        <button className="unlock" onClick={unlock}>
          <Icon name="chevronUp" size={20} />
          Нажми, чтобы открыть
        </button>
      </div>
    </div>
  )
}

/* --------------------------------- Инспектор ------------------------------- */

export function Inspector({ onAdd }: { onAdd: () => void }) {
  const { account, setSettings, runTask, thinking, ask, navigate, toast, setConnector, now } = useStore()
  const layout = account.settings.widgetLayout
  const today = account.reminders.filter((r) => r.date === todayISO())
  const done = today.filter((r) => r.done).length
  const overdue = account.reminders.filter((r) => !r.done && reminderDate(r) < now)

  return (
    <div className="inspector">
      <div className="panel">
        <div className="row" style={{ marginBottom: 10 }}>
          <h3>Виджеты на экране</h3>
          <span className="pill">{layout}</span>
        </div>
        <div className="seg" style={{ marginBottom: 10 }}>
          {(['2x2', '4x2', '4x4'] as const).map((s) => (
            <button key={s} className={layout === s ? 'on' : ''} onClick={() => setSettings({ widgetLayout: s })}>{s}</button>
          ))}
        </div>
        {DEFAULT_WIDGETS.map((w) => {
          const on = account.settings.activeWidgets.includes(w.id)
          return (
            <button
              key={w.id}
              className={`widget-pick${on ? ' on' : ''}`}
              style={{ marginBottom: 6 }}
              onClick={() =>
                setSettings({
                  activeWidgets: on
                    ? account.settings.activeWidgets.filter((x) => x !== w.id)
                    : [...account.settings.activeWidgets, w.id],
                })
              }
            >
              <span style={{ flex: 1 }}>
                <span className="nm">{w.title}</span>
                <span className="cap" style={{ display: 'block' }}>{w.caption}</span>
              </span>
              <span className="toggle">{on ? <Icon name="check" size={15} /> : <Icon name="plus" size={15} />}</span>
            </button>
          )
        })}
        <div className="hint" style={{ marginTop: 6 }}>
          Виджет «Сегодня» меняет размер: {layout}. Агенты могут сделать это за тебя — «поставь виджет 4×4».
        </div>
      </div>

      <div className="panel">
        <div className="row" style={{ marginBottom: 10 }}>
          <h3>Агенты</h3>
          <button className="link-btn" onClick={() => navigate('agents')}>открыть</button>
        </div>
        {AGENTS.map((a) => (
          <div key={a.id} className="log-item">
            <span className={`log-dot ${a.chief ? 'ok' : ''}`} />
            <div style={{ flex: 1 }}>
              <div className="log-agent">{a.name}</div>
              <div className="log-text" style={{ fontSize: 11.5 }}>{a.role}</div>
            </div>
          </div>
        ))}
        <div className="btn-row" style={{ marginTop: 10, flexWrap: 'wrap' }}>
          <button className="btn ghost sm" onClick={() => runTask('audit')} disabled={thinking}><Icon name="shield" size={14} /> Аудит</button>
          <button className="btn ghost sm" onClick={() => runTask('reschedule')} disabled={thinking}><Icon name="chart" size={14} /> Разгрузить вечер</button>
          <button className="btn ghost sm" onClick={() => runTask('demo')} disabled={thinking}><Icon name="play" size={14} /> Демо</button>
        </div>
      </div>

      <div className="panel">
        <div className="row" style={{ marginBottom: 8 }}>
          <h3>Контракт инструментов</h3>
          <span className="pill">MCP</span>
        </div>
        <div className="hint" style={{ marginBottom: 8 }}>
          Нажми, чтобы агент прогнал реальный вызов инструмента и проверил результат.
        </div>
        {TOOLS.slice(0, 5).map((t) => (
          <button key={t.name} className="widget-pick" style={{ marginBottom: 5 }} onClick={() => { navigate('agents'); ask(`Вызови инструмент ${t.name}: ${t.desc}`) }}>
            <span className="mono" style={{ flex: 1 }}>{t.name}</span>
            <Icon name="play" size={13} style={{ color: 'var(--txt-3)' }} />
          </button>
        ))}
        <button className="link-btn" onClick={() => navigate('connectors')}>все коннекторы →</button>
      </div>

      <div className="panel">
        <h3>Состояние</h3>
        <div className="kv"><span className="k">Сегодня</span><span>{done} / {today.length}</span></div>
        <div className="kv"><span className="k">Просрочено</span><span style={{ color: overdue.length ? 'var(--warn)' : undefined }}>{overdue.length}</span></div>
        <div className="kv"><span className="k">Всего записей</span><span>{account.reminders.length}</span></div>
        <div className="kv"><span className="k">Подключено</span><span>{account.connectors.filter((c) => c.connected).length} из {account.connectors.length}</span></div>
        <div className="btn-row" style={{ marginTop: 10 }}>
          <button className="btn ghost sm" onClick={onAdd}><Icon name="plus" size={14} /> Напоминание</button>
          <button
            className="btn ghost sm"
            onClick={() => {
              setConnector('browser', { connected: account.connectors.find((c) => c.id === 'browser')?.connected ? false : true })
              toast('Системные уведомления переключены', 'info')
            }}
          >
            <Icon name="bell" size={14} /> Пуш
          </button>
        </div>
        <div className="hint" style={{ marginTop: 8 }}>
          {overdue.length ? `Просрочено ${overdue.length} ${plural(overdue.length, 'запись', 'записи', 'записей')} — попроси «разгрузи вечер».` : 'Просрочек нет. Расписание ровное.'}
        </div>
      </div>
    </div>
  )
}

export function fmtTimeShort(t: number) {
  return fmtClock(new Date(t))
}

export { timeToMinutes }
