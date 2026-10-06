import { useEffect, useState } from 'react'
import { Icon } from './components/Icon'
import { Inspector, LockScreen, Toasts } from './components/Extras'
import { HomeScreen, WidgetsScreen, CalendarScreen, NotificationsScreen, ProfileScreen } from './components/Screens'
import { AgentsScreen } from './components/AgentsScreen'
import { ConnectorsScreen } from './components/ConnectorsScreen'
import { ReminderSheet } from './components/ReminderSheet'
import { useStore } from './store'
import type { Reminder, Screen } from './types'
import { fmtLongDate, reminderDate, todayISO } from './lib/date'

const NAV: { id: Screen; label: string; icon: string }[] = [
  { id: 'home', label: 'Главная', icon: 'home' },
  { id: 'calendar', label: 'Календарь', icon: 'calendar' },
  { id: 'widgets', label: 'Виджеты', icon: 'grid' },
  { id: 'agents', label: 'Агенты', icon: 'sparkles' },
  { id: 'connectors', label: 'Коннекторы', icon: 'link' },
  { id: 'profile', label: 'Профиль', icon: 'user' },
]

export default function App() {
  const { account, screen, navigate, thinking, now, lock, toast, setSettings } = useStore()
  const [editing, setEditing] = useState<Reminder | 'new' | null>(null)

  useEffect(() => {
    document.body.className = account.settings.theme === 'black' ? 'theme-black' : ''
  }, [account.settings.theme])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setEditing(null)
      if (e.key === 'n' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault()
        setEditing('new')
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const today = account.reminders.filter((r) => r.date === todayISO())
  const unread = account.notifications.filter((n) => !n.read).length
  const overdue = account.reminders.filter((r) => !r.done && reminderDate(r) < now).length
  const activeAgents = thinking ? 5 : 0

  const tabs: { id: Screen; label: string; icon: string; badge?: number }[] = [
    { id: 'home', label: 'Главная', icon: 'home' },
    { id: 'calendar', label: 'Календарь', icon: 'calendar' },
    { id: 'notifications', label: 'Напоминания', icon: 'bell', badge: unread },
    { id: 'profile', label: 'Профиль', icon: 'user' },
  ]

  return (
    <>
      <div className="shell">
        {/* ------------------------------ Сайдбар ---------------------------- */}
        <aside className="side">
          <div className="brand">
            <div className="brand-mark"><Icon name="bell" size={19} /></div>
            <div>
              <div className="brand-name">Memento</div>
              <div className="brand-sub">Напоминания · версия с ИИ-агентами</div>
            </div>
          </div>

          <nav className="nav">
            {NAV.map((n) => (
              <button key={n.id} className={`nav-item${screen === n.id ? ' active' : ''}`} onClick={() => navigate(n.id)}>
                <Icon name={n.icon} size={18} />
                <span className="lbl">{n.label}</span>
                {n.id === 'agents' && thinking ? <span className="badge hot">работает</span> : null}
                {n.id === 'connectors' ? <span className="badge">{account.connectors.filter((c) => c.connected).length}/{account.connectors.length}</span> : null}
                {n.id === 'calendar' && overdue ? <span className="badge hot">{overdue}</span> : null}
              </button>
            ))}
          </nav>

          <div className="panel">
            <div className="row" style={{ marginBottom: 8 }}>
              <h3>Пайплайн агентов</h3>
              <span className={`pill ${thinking ? 'run' : 'ok'}`}>{thinking ? 'работает' : 'готов'}</span>
            </div>
            <p>
              Claude Sonnet 5: главный оркестратор ставит задачу, парсер разбирает фразу, планировщик считает время,
              редактор применяет изменения, валидатор перечитывает базу и подтверждает каждую операцию.
            </p>
            <div className="btn-row" style={{ marginTop: 10 }}>
              <button className="btn ghost sm" onClick={() => navigate('agents')}>
                <Icon name="sparkles" size={14} /> {activeAgents ? 'Смотреть работу' : 'Дать задачу'}
              </button>
              <button className="btn ghost sm" onClick={lock}>
                <Icon name="lock" size={14} /> Экран блокировки
              </button>
            </div>
          </div>

          <div className="panel">
            <h3>Горячие клавиши</h3>
            <div className="kv"><span className="k">Новое напоминание</span><span className="mono">⌘/Ctrl + N</span></div>
            <div className="kv"><span className="k">Закрыть шторку</span><span className="mono">Esc</span></div>
            <div className="kv"><span className="k">Спросить агента</span><span className="k">экран «Агенты»</span></div>
          </div>

          <div className="panel">
            <h3>Данные</h3>
            <p>Всё хранится локально в браузере. Демо-данные можно вернуть в профиле.</p>
            <button className="btn ghost sm" style={{ marginTop: 10, width: '100%' }} onClick={() => { setSettings({ theme: account.settings.theme === 'black' ? 'dark' : 'black' }); toast('Тема переключена', 'info') }}>
              <Icon name={account.settings.theme === 'black' ? 'sun' : 'moon'} size={14} /> Сменить тему
            </button>
          </div>
        </aside>

        {/* ------------------------------- Телефон --------------------------- */}
        <main className="stage">
          <div className="phone-frame">
            <div className="phone">
              <span className="bg-orb orb-1" />
              <span className="bg-orb orb-2" />
              <span className="bg-orb orb-3" />

              <div className="statusbar">
                <span>{fmtLongDate(new Date(now)).split(',')[0]}, {today.length} событий</span>
                <span className="clock-mini">{new Date(now).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}</span>
              </div>

              <div className="phone-scroll" key={screen}>
                {screen === 'home' ? <HomeScreen onAdd={() => setEditing('new')} onEdit={(r) => setEditing(r)} /> : null}
                {screen === 'widgets' ? <WidgetsScreen onAdd={() => setEditing('new')} /> : null}
                {screen === 'calendar' ? <CalendarScreen onAdd={() => setEditing('new')} onEdit={(r) => setEditing(r)} /> : null}
                {screen === 'agents' ? <AgentsScreen /> : null}
                {screen === 'connectors' ? <ConnectorsScreen /> : null}
                {screen === 'profile' ? <ProfileScreen /> : null}
              </div>

              {screen === 'agents' ? null : (
                <button className="fab" onClick={() => setEditing('new')} aria-label="Новое напоминание">
                  <Icon name="plus" size={24} strokeWidth={1.8} />
                </button>
              )}

              <nav className="tabbar">
                {tabs.map((t) => (
                  <button key={t.id} className={`tab${screen === t.id ? ' active' : ''}`} onClick={() => navigate(t.id)}>
                    <span style={{ position: 'relative' }}>
                      <Icon name={t.icon} size={20} />
                      {t.badge ? (
                        <span style={{
                          position: 'absolute', top: -3, right: -6, minWidth: 15, height: 15, borderRadius: 8,
                          background: '#e8c37a', color: '#14161b', fontSize: 9.5, display: 'grid', placeItems: 'center', padding: '0 3px',
                        }}>{t.badge}</span>
                      ) : null}
                    </span>
                    {t.label}
                  </button>
                ))}
              </nav>

              {screen === 'notifications' ? (
                <NotificationsOverlay />
              ) : null}

              {account.locked ? <LockScreen /> : null}

              {editing ? (
                <ReminderSheet
                  reminder={editing === 'new' ? 'new' : editing}
                  onClose={() => setEditing(null)}
                />
              ) : null}
            </div>
          </div>
        </main>

        {/* ------------------------------- Правый ---------------------------- */}
        <Inspector onAdd={() => setEditing('new')} />
      </div>

      <Toasts />
    </>
  )
}

/** Центр уведомлений открывается отдельным экраном телефона (та же верстка, но поверх). */
function NotificationsOverlay() {
  const { navigate } = useStore()
  return (
    <div className="sheet" style={{ zIndex: 15 }} onClick={() => navigate('home')}>
      <div className="sheet-card" style={{ maxHeight: '78%', height: '78%' }} onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <button className="link-btn" onClick={() => navigate('home')}>Закрыть</button>
          <div className="ttl">Центр уведомлений</div>
          <span style={{ width: 56 }} />
        </div>
        <div className="sheet-body">
          <NotificationsScreen />
        </div>
      </div>
    </div>
  )
}
