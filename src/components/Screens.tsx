import { useMemo, useState } from 'react'
import { Icon } from './Icon'
import { Ring, Switch } from './Sheet'
import { useStore } from '../store'
import {
  dayLabel, fmtLongDate, fmtShortDate, greetingFor, greetingSub, MONTHS_NOM, WEEK_SHORT,
  addDaysISO, daysInMonth, fromISO, mondayIndex, plural, priorityLabel, relativeLabel, reminderDate,
  repeatLabel, timeToMinutes, toISO, todayISO,
} from '../lib/date'
import { DEFAULT_WIDGETS } from '../agent/agents'
import { renderWidget, TodayWidget } from './Widgets'
import type { Reminder } from '../types'

/* --------------------------------- Главная -------------------------------- */

export function HomeScreen({ onAdd, onEdit }: { onAdd: () => void; onEdit: (r: Reminder) => void }) {
  const { account, navigate, now } = useStore()
  const today = todayISO()
  const list = account.reminders.filter((r) => r.date === today)
  const done = list.filter((r) => r.done).length
  const active = DEFAULT_WIDGETS.filter((w) => account.settings.activeWidgets.includes(w.id) && w.id !== 'today')

  return (
    <>
      <div className="screen-head">
        <div>
          <h1 className="screen-title">
            {greetingFor(new Date(now))},<br />
            {account.settings.userName}
          </h1>
          <div className="screen-sub">{greetingSub(new Date(now), list.filter((r) => !r.done).length)}</div>
        </div>
        <Ring value={done} max={list.length} size={58} label="Выполнено сегодня" />
      </div>

      <TodayWidget onOpen={() => navigate('calendar')} onAdd={onAdd} onEdit={onEdit} />
      {active.map((w) => (
        <div key={w.id} style={{ marginTop: 12 }}>
          {renderWidget(w, { onOpen: () => navigate('calendar'), onAdd: onAdd, onEdit })}
        </div>
      ))}

      <div className="section-title">Скоро</div>
      <div className="panel">
        {account.reminders
          .filter((r) => !r.done && r.date > today)
          .sort((a, b) => a.date.localeCompare(b.date) || timeToMinutes(a.time) - timeToMinutes(b.time))
          .slice(0, 4)
          .map((r) => (
            <button key={r.id} className="kv" style={{ width: '100%', textAlign: 'left' }} onClick={() => onEdit(r)}>
              <span style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
                <Icon name={r.icon} size={16} style={{ color: 'var(--txt-2)' }} />
                <span>
                  {r.title}
                  <span className="rem-meta" style={{ display: 'block' }}>{dayLabel(r.date)} · {r.time}{r.repeat !== 'none' ? ` · ${repeatLabel(r.repeat)}` : ''}</span>
                </span>
              </span>
              <Icon name="chevronRight" size={16} style={{ color: 'var(--txt-3)' }} />
            </button>
          ))}
        {!account.reminders.filter((r) => !r.done && r.date > today).length ? (
          <div className="empty">Дальше по календарю пусто</div>
        ) : null}
      </div>
    </>
  )
}

/* --------------------------------- Виджеты -------------------------------- */

export function WidgetsScreen({ onAdd }: { onAdd: () => void }) {
  const { account, setSettings } = useStore()
  const layout = account.settings.widgetLayout
  const sizes: { id: '2x2' | '4x2' | '4x4'; label: string; cap: string }[] = [
    { id: '2x2', label: '2 × 2', cap: 'компактно' },
    { id: '4x2', label: '4 × 2', cap: 'стандарт' },
    { id: '4x4', label: '4 × 4', cap: 'расписание' },
  ]

  return (
    <>
      <div className="page-head">
        <div>
          <div className="h">Виджеты</div>
          <div className="hint">Один тап — и главный экран перестроится</div>
        </div>
      </div>

      <div className="seg" style={{ marginBottom: 12 }}>
        {sizes.map((s) => (
          <button key={s.id} className={layout === s.id ? 'on' : ''} onClick={() => setSettings({ widgetLayout: s.id })}>
            {s.label}<span className="hint" style={{ display: 'block', fontSize: 10 }}>{s.cap}</span>
          </button>
        ))}
      </div>

      <div className="hint" style={{ marginBottom: 12 }}>
        Размер «Сегодня»: <b>{layout}</b>. Переключи кнопками выше или скажи агенту «поставь виджет 4×4».
      </div>

      <TodayWidget onOpen={onAdd} onAdd={onAdd} />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginTop: 12 }}>
        <TodayPreviewMini />
        <NextMini />
      </div>

      <div className="section-title">Все виджеты</div>
      {DEFAULT_WIDGETS.map((w) => {
        const on = account.settings.activeWidgets.includes(w.id)
        return (
          <button
            key={w.id}
            className={`widget-pick${on ? ' on' : ''}`}
            onClick={() =>
              setSettings({
                activeWidgets: on
                  ? account.settings.activeWidgets.filter((x) => x !== w.id)
                  : [...account.settings.activeWidgets, w.id],
              })
            }
          >
            <Icon name="grid" size={17} style={{ color: 'var(--txt-2)' }} />
            <span style={{ flex: 1 }}>
              <span className="nm">{w.title}</span>
              <span className="cap" style={{ display: 'block' }}>{w.caption}</span>
            </span>
            <span className="toggle">{on ? <Icon name="check" size={16} /> : <Icon name="plus" size={16} />}</span>
          </button>
        )
      })}
      <div className="hint" style={{ marginTop: 8 }}>
        Включённые виджеты появляются на главном экране под «Сегодня». Виджет «Расписание на день» (4×4) показывает таймлайн с кнопкой отложить.
      </div>
    </>
  )
}

function TodayPreviewMini() {
  const { account, snooze } = useStore()
  const list = account.reminders.filter((r) => r.date === todayISO()).sort((a, b) => timeToMinutes(a.time) - timeToMinutes(b.time))
  return (
    <div className="widget" style={{ padding: 12 }}>
      <div className="widget-head" style={{ marginBottom: 4 }}>
        <Icon name="calendar" size={16} className="ico" />
        <div className="widget-title" style={{ fontSize: 13 }}>Сегодня</div>
        <div className="count">2×2</div>
      </div>
      {list.slice(0, 3).map((r) => (
        <div key={r.id} className="rem-row" style={{ padding: '6px 0' }}>
          <div className="rem-time" style={{ fontSize: 12, minWidth: 38 }}>{r.time}</div>
          <div className="rem-main"><div className="rem-title" style={{ fontSize: 12.5 }}>{r.title}</div></div>
        </div>
      ))}
      {list.length ? (
        <button className="mini-btn" style={{ marginTop: 6 }} onClick={() => snooze(list[0].id, 10)}>Отложить первую на 10 мин</button>
      ) : (
        <div className="hint">Пусто</div>
      )}
    </div>
  )
}

function NextMini() {
  const { account } = useStore()
  const next = account.reminders
    .filter((r) => !r.done && reminderDate(r) > Date.now() - 60000)
    .sort((a, b) => reminderDate(a) - reminderDate(b))[0]
  return (
    <div className="widget" style={{ padding: 12 }}>
      <div className="widget-head" style={{ marginBottom: 6 }}>
        <Icon name="clock" size={16} className="ico" />
        <div className="widget-title" style={{ fontSize: 13 }}>Следующее</div>
        <div className="count">2×2</div>
      </div>
      {next ? (
        <>
          <div style={{ fontSize: 13, fontWeight: 550, lineHeight: 1.35 }}>{next.title} — {next.time}</div>
          <div className="hint" style={{ marginTop: 5 }}>{relativeLabel(reminderDate(next), Date.now())}</div>
        </>
      ) : (
        <div className="hint">Свободно</div>
      )}
    </div>
  )
}

/* -------------------------------- Календарь ------------------------------- */

export function CalendarScreen({ onAdd, onEdit }: { onAdd: () => void; onEdit: (r: Reminder) => void }) {
  const { account, navigate } = useStore()
  const [cursor, setCursor] = useState(() => {
    const d = new Date()
    return { y: d.getFullYear(), m: d.getMonth() }
  })
  const [selected, setSelected] = useState(todayISO())
  const [filter, setFilter] = useState<'all' | 'active' | 'done'>('all')

  const cells = useMemo(() => {
    const first = mondayIndex(cursor.y, cursor.m, 1)
    const total = daysInMonth(cursor.y, cursor.m)
    const prevTotal = daysInMonth(cursor.y, cursor.m - 1)
    const out: { iso: string; day: number; dim: boolean }[] = []
    for (let i = first - 1; i >= 0; i--) {
      const day = prevTotal - i
      const d = new Date(cursor.y, cursor.m - 1, day)
      out.push({ iso: toISO(d), day, dim: true })
    }
    for (let i = 1; i <= total; i++) {
      out.push({ iso: `${cursor.y}-${String(cursor.m + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`, day: i, dim: false })
    }
    while (out.length % 7 !== 0) {
      const d = fromISO(out[out.length - 1].iso)
      d.setDate(d.getDate() + 1)
      out.push({ iso: toISO(d), day: d.getDate(), dim: true })
    }
    return out
  }, [cursor])

  const dayReminders = account.reminders
    .filter((r) => r.date === selected)
    .filter((r) => (filter === 'all' ? true : filter === 'active' ? !r.done : r.done))
    .sort((a, b) => timeToMinutes(a.time) - timeToMinutes(b.time))

  const shift = (delta: number) => {
    const d = new Date(cursor.y, cursor.m + delta, 1)
    setCursor({ y: d.getFullYear(), m: d.getMonth() })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="h">Календарь</div>
          <div className="hint">{dayReminders.length} {plural(dayReminders.length, 'напоминание', 'напоминания', 'напоминаний')} · {dayLabel(selected)}</div>
        </div>
        <button className="btn soft sm" onClick={onAdd}><Icon name="plus" size={15} /> Добавить</button>
      </div>

      <div className="widget">
        <div className="widget-head" style={{ marginBottom: 8 }}>
          <button className="chev" onClick={() => shift(-1)} aria-label="Предыдущий месяц"><Icon name="chevronLeft" size={18} /></button>
          <div className="widget-title">{MONTHS_NOM[cursor.m]} {cursor.y}</div>
          <button className="chev" onClick={() => shift(1)} aria-label="Следующий месяц"><Icon name="chevronRight" size={18} /></button>
          <button className="chip" style={{ marginLeft: 4 }} onClick={() => { const d = new Date(); setCursor({ y: d.getFullYear(), m: d.getMonth() }); setSelected(todayISO()) }}>Сегодня</button>
        </div>
        <div className="cal-head">{WEEK_SHORT.map((w) => <div key={w}>{w}</div>)}</div>
        <div className="cal-grid">
          {cells.map((c) => {
            const marks = account.reminders.filter((r) => r.date === c.iso && !r.done).slice(0, 3)
            return (
              <button
                key={c.iso}
                className={`cal-cell${c.dim ? ' dim' : ''}${selected === c.iso ? ' on' : ''}${c.iso === todayISO() ? ' today' : ''}`}
                onClick={() => setSelected(c.iso)}
              >
                {c.day}
                <span className="marks">{marks.map((m, i) => <i key={i} />)}</span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="seg" style={{ marginTop: 12 }}>
        <button className={filter === 'all' ? 'on' : ''} onClick={() => setFilter('all')}>Все</button>
        <button className={filter === 'active' ? 'on' : ''} onClick={() => setFilter('active')}>Активные</button>
        <button className={filter === 'done' ? 'on' : ''} onClick={() => setFilter('done')}>Выполненные</button>
      </div>

      <div className="section-title">{fmtLongDate(fromISO(selected))}</div>
      {dayReminders.length ? dayReminders.map((r) => (
        <div key={r.id} className="panel" style={{ marginBottom: 9, cursor: 'pointer' }} onClick={() => onEdit(r)}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
            <div className="agent-ico" style={{ width: 34, height: 34 }}><Icon name={r.icon} size={16} /></div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className={`rem-title${r.done ? ' done' : ''}`}>{r.title}</div>
              <div className="rem-meta">
                {r.time} · {priorityLabel(r.priority)}{r.repeat !== 'none' ? ` · ${repeatLabel(r.repeat)}` : ''}{r.place ? ` · ${r.place}` : ''}
              </div>
            </div>
            {r.priority === 'high' && !r.done ? <span className="pill warn">важное</span> : null}
            <ActionButtons r={r} />
          </div>
          {r.notes ? <div className="hint" style={{ marginTop: 8 }}>{r.notes}</div> : null}
        </div>
      )) : (
        <div className="panel">
          <div className="empty">
            <Icon name="calendar" size={22} />
            На этот день напоминаний нет
            <button className="mini-btn" onClick={onAdd}>Добавить напоминание</button>
          </div>
        </div>
      )}

      <div className="btn-row" style={{ marginTop: 12 }}>
        <button className="btn ghost sm" onClick={() => navigate('widgets')}><Icon name="grid" size={15} /> Настроить виджеты</button>
        <button className="btn ghost sm" onClick={() => navigate('agents')}><Icon name="sparkles" size={15} /> Спросить агента</button>
      </div>
    </>
  )
}

function ActionButtons({ r }: { r: Reminder }) {
  const { toggleDone, snooze } = useStore()
  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
      {!r.done ? (
        <button className="mini-btn" onClick={(e) => { e.stopPropagation(); snooze(r.id, 10) }} title="Отложить на 10 минут">+10 мин</button>
      ) : null}
      <button
        className={`check${r.done ? ' on' : ''}`}
        onClick={(e) => { e.stopPropagation(); toggleDone(r.id) }}
        aria-label="Отметить выполненным"
      >
        <Icon name="check" size={12} strokeWidth={2.4} />
      </button>
    </div>
  )
}

/* ------------------------------ Уведомления ------------------------------- */

export function NotificationsScreen() {
  const { account, clearNotifications, markNotificationsRead, snooze, toggleDone, now } = useStore()
  const unread = account.notifications.filter((n) => !n.read).length
  return (
    <>
      <div className="page-head">
        <div>
          <div className="h">Центр уведомлений</div>
          <div className="hint">{account.notifications.length ? `${account.notifications.length} записей${unread ? ` · ${unread} новых` : ''}` : 'Пока пусто'}</div>
        </div>
        <div className="btn-row">
          <button className="btn ghost sm" onClick={markNotificationsRead} disabled={!unread}>Прочитано</button>
          <button className="btn ghost sm" onClick={clearNotifications} disabled={!account.notifications.length}>Очистить</button>
        </div>
      </div>

      {account.notifications.map((n) => {
        const rem = account.reminders.find((r) => r.id === n.reminderId)
        return (
          <div key={n.id} className={`notif${n.read ? '' : ' unread'}`}>
            <div className="agent-ico" style={{ width: 38, height: 38 }}>
              <Icon name={rem?.icon || (n.kind === 'done' ? 'check' : n.kind === 'snooze' ? 'repeat' : 'bell')} size={17} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="nm">Memento · {n.title}</div>
              <div className="body">{n.body}</div>
              <div className="tm"><Icon name="clock" size={12} /> {relativeLabel(n.at, now)}</div>
              {rem && !rem.done ? (
                <div className="tl-actions">
                  <button className="mini-btn" onClick={() => snooze(rem.id, 10)}>Отложить 10 мин</button>
                  <button className="mini-btn" onClick={() => toggleDone(rem.id)}>Выполнено</button>
                </div>
              ) : null}
            </div>
          </div>
        )
      })}

      {!account.notifications.length ? (
        <div className="panel">
          <div className="empty">
            <Icon name="bell" size={22} />
            Здесь появятся сработавшие напоминания
            <div className="hint">Проверь, что включены уведомления и звук в профиле.</div>
          </div>
        </div>
      ) : null}
    </>
  )
}

/* -------------------------------- Профиль --------------------------------- */

export function ProfileScreen() {
  const {
    account, accounts, setSettings, switchAccount, addAccount, resetDemo, navigate, toast,
  } = useStore()
  const [newName, setNewName] = useState('')
  const stats = useMemo(() => {
    const all = account.reminders
    const done = all.filter((r) => r.done).length
    const today = all.filter((r) => r.date === todayISO()).length
    return { all: all.length, done, today, active: all.filter((r) => !r.done).length }
  }, [account.reminders])

  return (
    <>
      <div className="page-head">
        <div className="h">Профиль</div>
      </div>

      <div className="widget" style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
        <div className="avatar">{account.settings.userName.charAt(0).toUpperCase()}</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 17, fontWeight: 600 }}>{account.settings.userName}</div>
          <div className="hint">Серия: {stats.done} выполнено · {stats.active} в плане</div>
          <div className="chips" style={{ marginTop: 8 }}>
            <span className="chip"><Icon name="calendar" size={12} /> Сегодня {stats.today}</span>
            <span className="chip"><Icon name="target" size={12} /> Всего {stats.all}</span>
          </div>
        </div>
      </div>

      <div className="section-title">Профили</div>
      <div className="panel">
        {accounts.map((a) => (
          <button key={a.id} className="kv" style={{ width: '100%', textAlign: 'left' }} onClick={() => switchAccount(a.id)}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <span className="agent-ico" style={{ width: 28, height: 28, borderRadius: 9, fontSize: 12 }}>{a.name.charAt(0).toUpperCase()}</span>
              {a.name}
            </span>
            {a.id === account.id ? <span className="pill ok"><Icon name="check" size={11} />активный</span> : <span className="hint">переключить</span>}
          </button>
        ))}
        <div className="btn-row" style={{ marginTop: 10 }}>
          <div className="field" style={{ margin: 0, flex: 1 }}>
            <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Имя нового профиля" />
          </div>
          <button
            className="btn soft sm"
            onClick={() => {
              if (!newName.trim()) return toast('Введи имя профиля', 'warn')
              addAccount(newName.trim())
              setNewName('')
            }}
          >
            <Icon name="plus" size={15} /> Создать
          </button>
        </div>
      </div>

      <div className="section-title">Настройки</div>
      <div className="field">
        <span className="f-ico"><Icon name="user" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Имя на главном экране</div>
          <input value={account.settings.userName} onChange={(e) => setSettings({ userName: e.target.value })} />
        </div>
      </div>

      <div className="field" style={{ marginTop: 9 }}>
        <span className="f-ico"><Icon name="grid" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Размер виджета «Сегодня»</div>
          <div className="f-value">{account.settings.widgetLayout}</div>
        </div>
        <div className="seg" style={{ padding: 3 }}>
          {(['2x2', '4x2', '4x4'] as const).map((s) => (
            <button key={s} className={account.settings.widgetLayout === s ? 'on' : ''} onClick={() => setSettings({ widgetLayout: s })} style={{ padding: '5px 8px' }}>{s}</button>
          ))}
        </div>
      </div>

      <div className="field" style={{ marginTop: 9 }}>
        <span className="f-ico"><Icon name="sparkles" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Тема</div>
        </div>
        <div className="seg" style={{ padding: 3 }}>
          <button className={account.settings.theme === 'dark' ? 'on' : ''} onClick={() => setSettings({ theme: 'dark' })} style={{ padding: '5px 10px' }}>Графит</button>
          <button className={account.settings.theme === 'black' ? 'on' : ''} onClick={() => setSettings({ theme: 'black' })} style={{ padding: '5px 10px' }}>Уголь</button>
        </div>
      </div>

      {[
        { key: 'notificationsEnabled' as const, icon: 'bell', label: 'Уведомления', hint: 'Показывать всплывающие карточки' },
        { key: 'soundEnabled' as const, icon: 'activity', label: 'Звук', hint: 'Короткий сигнал при срабатывании' },
        { key: 'verification' as const, icon: 'shield', label: 'Проверка агентом', hint: 'Валидатор перечитывает базу после каждой операции' },
      ].map((row) => (
        <div key={row.key} className="field" style={{ marginTop: 9 }}>
          <span className="f-ico"><Icon name={row.icon} size={18} /></span>
          <div className="f-body">
            <div className="f-label">{row.label}</div>
            <div className="hint">{row.hint}</div>
          </div>
          <Switch on={Boolean(account.settings[row.key])} onChange={(v) => setSettings({ [row.key]: v } as any)} />
        </div>
      ))}

      <div className="section-title">Данные</div>
      <div className="btn-row">
        <button className="btn ghost sm" onClick={() => navigate('connectors')}><Icon name="link" size={15} /> Коннекторы</button>
        <button className="btn ghost sm" onClick={() => { resetDemo(); navigate('home') }}><Icon name="refresh" size={15} /> Сбросить демо</button>
      </div>
      <div className="hint" style={{ marginTop: 10 }}>
        Все данные хранятся локально в браузере и никуда не отправляются. Реальный Claude API можно подключить в «Коннекторах».
      </div>
      <div style={{ height: 14 }} />
    </>
  )
}
