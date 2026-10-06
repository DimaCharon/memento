import { Icon } from './Icon'
import { Ring } from './Sheet'
import { useStore } from '../store'
import {
  dayLabel, fmtLongDate, fmtShortDate, priorityLabel, relativeLabel, reminderDate, timeToMinutes, todayISO, WEEK_SHORT, addDaysISO, fromISO,
} from '../lib/date'
import type { Reminder, WidgetDef } from '../types'

function byTime(a: Reminder, b: Reminder) {
  return timeToMinutes(a.time) - timeToMinutes(b.time)
}

function todayList(reminders: Reminder[]) {
  const t = todayISO()
  return reminders.filter((r) => r.date === t).sort(byTime)
}

function RemRow({ r, showPriority = true, showCheck = true, compact }: { r: Reminder; showPriority?: boolean; showCheck?: boolean; compact?: boolean }) {
  const { toggleDone } = useStore()
  const overdue = !r.done && reminderDate(r) < Date.now()
  return (
    <div className="rem-row" style={compact ? { padding: '7px 0' } : undefined}>
      <div className="rem-time" style={overdue ? { color: 'var(--warn)' } : undefined}>{r.time}</div>
      <div className="rem-main">
        <div className={`rem-title${r.done ? ' done' : ''}`}>{r.title}</div>
        {!compact && !r.done && (r.place || r.notes) ? <div className="rem-meta">{r.place || r.notes}</div> : null}
      </div>
      {showPriority ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span className={`dot ${r.priority}`} />
          <span className="prio-label">{priorityLabel(r.priority)}</span>
        </div>
      ) : null}
      {showCheck ? (
        <button className={`check${r.done ? ' on' : ''}`} onClick={() => toggleDone(r.id)} aria-label="Выполнено">
          <Icon name="check" size={12} strokeWidth={2.4} />
        </button>
      ) : null}
    </div>
  )
}

export function TodayWidget({ onOpen, onAdd, onEdit, showPriority = true }: { onOpen?: () => void; onAdd?: () => void; onEdit?: (r: Reminder) => void; showPriority?: boolean }) {
  const { account, snooze, toggleDone } = useStore()
  const list = todayList(account.reminders)
  const done = list.filter((r) => r.done).length

  if (account.settings.widgetLayout === '2x2') {
    return (
      <div className="widget">
        <div className="widget-head">
          <Icon name="calendar" size={19} className="ico" />
          <div>
            <div className="widget-title">Сегодня</div>
            <div className="widget-sub">{list.length} {list.length === 1 ? 'напоминание' : 'напоминаний'}</div>
          </div>
          <button className="chev" onClick={onOpen} aria-label="Открыть">
            <Icon name="chevronRight" size={18} />
          </button>
        </div>
        {list.slice(0, 3).map((r) => <RemRow key={r.id} r={r} showPriority={false} showCheck={false} compact />)}
        {list.length > 3 ? <div className="hint" style={{ marginTop: 4 }}>и ещё {list.length - 3}</div> : null}
        {!list.length ? <div className="empty">На сегодня пусто</div> : null}
      </div>
    )
  }

  if (account.settings.widgetLayout === '4x4') {
    return (
      <div className="widget">
        <div className="widget-head">
          <Icon name="clock" size={19} className="ico" />
          <div className="widget-title">Сегодня</div>
          <div className="count">{done} из {list.length}</div>
        </div>
        {list.length ? (
          <div className="timeline">
            {list.map((r) => (
              <div key={r.id} className={`tl-item${r.done ? ' done' : ''}`}>
                <div className="tl-time">{r.time}</div>
                <div className="tl-body">
                  <div className={`rem-title${r.done ? ' done' : ''}`}>{r.title}</div>
                  {r.place || r.notes ? <div className="rem-meta">{r.place || r.notes}</div> : null}
                  <div className="tl-actions">
                    <button className="mini-btn" onClick={() => snooze(r.id, 10)}>+10 мин</button>
                    {onEdit ? <button className="mini-btn" onClick={() => onEdit(r)}>Изменить</button> : null}
                    <button className={`check${r.done ? ' on' : ''}`} style={{ width: 24, height: 24 }} onClick={() => toggleDone(r.id)} aria-label="Выполнено">
                      <Icon name="check" size={12} strokeWidth={2.4} />
                    </button>
                  </div>
                </div>
                <button className="chev" onClick={onOpen} aria-label="Календарь">
                  <Icon name="calendar" size={17} />
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty">
            <Icon name="calendar" size={22} />
            На сегодня нет напоминаний
            {onAdd ? <button className="mini-btn" onClick={onAdd}>Добавить</button> : null}
          </div>
        )}
        <div className="hint" style={{ marginTop: 10 }}>{fmtLongDate(new Date())}</div>
      </div>
    )
  }

  return (
    <div className="widget">
      <div className="widget-head">
        <Icon name="calendar" size={19} className="ico" />
        <div>
          <div className="widget-title">Сегодня</div>
          <div className="widget-sub">{list.length} {list.length === 1 ? 'напоминание' : 'напоминаний'}</div>
        </div>
        <button className="chev" onClick={onOpen} aria-label="Открыть">
          <Icon name="chevronRight" size={18} />
        </button>
      </div>
      {list.slice(0, 4).map((r) => <RemRow key={r.id} r={r} showPriority={showPriority} />)}
      {!list.length ? <div className="empty">На сегодня пусто — нажми «+», чтобы добавить</div> : null}
    </div>
  )
}

export function NextWidget() {
  const { account, navigate } = useStore()
  const next = account.reminders
    .filter((r) => !r.done && (r.snoozedUntil ?? reminderDate(r)) > Date.now() - 60000)
    .sort((a, b) => (a.snoozedUntil ?? reminderDate(a)) - (b.snoozedUntil ?? reminderDate(b)))[0]
  return (
    <div className="widget" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="widget-head">
        <Icon name="clock" size={19} className="ico" />
        <div className="widget-title">Следующее</div>
      </div>
      {next ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
          <div style={{ fontSize: 15.5, fontWeight: 560, lineHeight: 1.3 }}>
            {next.title} — {next.time}
          </div>
          <div className="hint">{relativeLabel((next.snoozedUntil ?? reminderDate(next)), Date.now())}{next.place ? ` · ${next.place}` : ''}</div>
          <div className="spacer" />
          <button className="mini-btn" style={{ alignSelf: 'flex-start' }} onClick={() => navigate('calendar')} title="Открыть календарь">
            <Icon name="calendar" size={14} /> {dayLabel(next.date)}
          </button>
        </div>
      ) : (
        <div className="empty" style={{ flex: 1 }}>
          <Icon name="check" size={22} />
          Свободно
        </div>
      )}
    </div>
  )
}

export function ProgressWidget() {
  const { account } = useStore()
  const list = todayList(account.reminders)
  const done = list.filter((r) => r.done).length
  const pct = list.length ? Math.round((done / list.length) * 100) : 0
  return (
    <div className="widget" style={{ height: '100%' }}>
      <div className="widget-head">
        <Icon name="target" size={19} className="ico" />
        <div className="widget-title">Прогресс дня</div>
      </div>
      <div className="progress-wrap">
        <div className="progress-num">
          {done}<small> / {list.length}</small>
        </div>
        <div className="bar"><i style={{ width: `${pct}%` }} /></div>
        <div className="hint">{pct >= 100 && list.length ? 'Всё выполнено. Отличный день!' : `Выполнено ${pct}% плана`}</div>
      </div>
    </div>
  )
}

export function WeekWidget() {
  const { account, navigate } = useStore()
  const t = todayISO()
  const days = Array.from({ length: 7 }, (_, i) => addDaysISO(t, i - 3))
  const counts = days.map((d) => account.reminders.filter((r) => r.date === d && !r.done).length)
  const max = Math.max(1, ...counts)
  return (
    <div className="widget">
      <div className="widget-head">
        <Icon name="chart" size={19} className="ico" />
        <div className="widget-title">Ближайшая неделя</div>
        <button className="chev" onClick={() => navigate('calendar')}><Icon name="chevronRight" size={18} /></button>
      </div>
      <div style={{ display: 'flex', gap: 6, alignItems: 'flex-end', height: 78 }}>
        {days.map((d, i) => {
          const isToday = d === t
          return (
            <button
              key={d}
              onClick={() => navigate('calendar')}
              style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}
              title={fmtShortDate(fromISO(d))}
            >
              <span style={{ fontSize: 11, color: 'var(--txt-3)' }}>{counts[i]}</span>
              <span
                style={{
                  width: '100%', borderRadius: 7, height: Math.max(6, (counts[i] / max) * 52),
                  background: isToday ? 'linear-gradient(180deg,#dfe4ef,#98a1b6)' : 'rgba(255,255,255,0.14)',
                }}
              />
              <span style={{ fontSize: 10.5, color: isToday ? 'var(--txt)' : 'var(--txt-3)' }}>
                {WEEK_SHORT[(fromISO(d).getDay() + 6) % 7]}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function ScheduleWidget({ onEdit }: { onEdit?: (r: Reminder) => void } = {}) {
  const { account, snooze, toggleDone } = useStore()
  const list = todayList(account.reminders)
  const done = list.filter((r) => r.done).length
  return (
    <div className="widget">
      <div className="widget-head">
        <Icon name="clock" size={19} className="ico" />
        <div className="widget-title">Сегодня</div>
        <div className="count">{done} из {list.length}</div>
      </div>
      <div className="timeline">
        {list.map((r) => (
          <div key={r.id} className={`tl-item${r.done ? ' done' : ''}`}>
            <div className="tl-time">{r.time}</div>
            <div className="tl-body">
              <div className={`rem-title${r.done ? ' done' : ''}`}>{r.title}</div>
              <div className="rem-meta">
                {r.place || r.notes || priorityLabel(r.priority)}
                {r.repeat !== 'none' && !r.done ? ' · с повтором' : ''}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
              <button className="mini-btn" onClick={() => snooze(r.id, 10)} title="Отложить на 10 минут">+10</button>
              {onEdit ? <button className="mini-btn" onClick={() => onEdit(r)} title="Изменить">✎</button> : null}
            </div>
          </div>
        ))}
        {!list.length ? <div className="empty">День свободен</div> : null}
      </div>
      <div className="hint" style={{ marginTop: 10 }}>{fmtLongDate(new Date())}</div>
    </div>
  )
}

export function AgentWidget() {
  const { account, navigate, thinking } = useStore()
  const last = [...account.logs].reverse().find((l) => l.status === 'ok' || l.status === 'warn')
  return (
    <div className="widget" style={{ cursor: 'pointer' }} onClick={() => navigate('agents')}>
      <div className="widget-head">
        <Icon name="sparkles" size={19} className="ico" />
        <div className="widget-title">Агент</div>
        {thinking ? <span className="pill run"><i className="pulse" />работает</span> : <span className="pill ok"><Icon name="check" size={11} />готов</span>}
      </div>
      {last ? (
        <>
          <div className="log-agent">{last.agent} · <span className="mono">{last.model}</span></div>
          <div className="hint" style={{ marginTop: 6 }}>{last.text}</div>
        </>
      ) : (
        <div className="hint">Агенты ещё не работали. Открой экран агентов и дай задачу.</div>
      )}
    </div>
  )
}

export function renderWidget(def: WidgetDef, opts: { onOpen?: () => void; onAdd?: () => void; onEdit?: (r: Reminder) => void }) {
  switch (def.id) {
    case 'today': return <TodayWidget {...opts} />
    case 'next': return <NextWidget />
    case 'progress': return <ProgressWidget />
    case 'week': return <WeekWidget />
    case 'schedule': return <ScheduleWidget onEdit={opts.onEdit} />
    case 'agent': return <AgentWidget />
    default: return null
  }
}
