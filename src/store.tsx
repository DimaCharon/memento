import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type {
  ChatMessage, Connector, LogEntry, NotificationItem, Priority, Reminder, Repeat, Screen, Settings,
} from './types'
import {
  addDaysISO, beep, nextOccurrenceISO, plural, reminderDate, todayISO, toISO, uid,
} from './lib/date'
import { AGENTS, CONNECTORS_SEED, DEFAULT_SETTINGS } from './agent/agents'
import { DEMO_PROMPTS, runAudit, runPipeline, runReschedule } from './agent/engine'
import type { AgentContext } from './agent/agents'

const KEY = 'memento.db.v1'

export interface Account {
  id: string
  name: string
  createdAt: number
  locked: boolean
  reminders: Reminder[]
  notifications: NotificationItem[]
  logs: LogEntry[]
  chat: ChatMessage[]
  settings: Settings
  connectors: Connector[]
}

interface DB {
  accounts: Account[]
  activeId: string
}

export interface Toast {
  id: string
  text: string
  kind: 'ok' | 'warn' | 'err' | 'info'
}

interface Store {
  account: Account
  accounts: Account[]
  screen: Screen
  toasts: Toast[]
  thinking: boolean
  agentStatus: Record<string, 'idle' | 'run' | 'ok' | 'warn' | 'err'>
  navigate: (s: Screen) => void
  lock: () => void
  unlock: () => void
  addReminder: (r: Partial<Reminder>) => string
  updateReminder: (id: string, patch: Partial<Reminder>) => void
  deleteReminder: (id: string) => void
  toggleDone: (id: string) => void
  snooze: (id: string, minutes: number) => void
  clearNotifications: () => void
  markNotificationsRead: () => void
  setSettings: (patch: Partial<Settings>) => void
  setConnector: (id: string, patch: Partial<Connector>) => void
  setConnectorField: (id: string, key: string, value: string) => void
  addAccount: (name: string) => void
  switchAccount: (id: string) => void
  resetDemo: () => void
  ask: (text: string) => Promise<void>
  runTask: (task: 'audit' | 'reschedule' | 'demo') => Promise<void>
  toast: (text: string, kind?: Toast['kind']) => void
  now: number
}

const Ctx = createContext<Store | null>(null)

/* --------------------------------- Сид ----------------------------------- */

function at(daysAhead: number, time: string) {
  return { date: addDaysISO(todayISO(), daysAhead), time }
}

function seedReminders(): Reminder[] {
  const now = new Date()
  const plus = (m: number) => {
    const d = new Date(now.getTime() + m * 60000)
    return { date: toISO(d), time: `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }
  }
  const mk = (
    title: string, when: { date: string; time: string }, extra: Partial<Reminder> = {},
  ): Reminder => ({
    id: uid('rem'),
    title,
    notes: '',
    icon: 'bell',
    repeat: 'none',
    priority: 'medium',
    place: '',
    leadMinutes: 10,
    done: false,
    firedAt: null,
    snoozedUntil: null,
    source: 'Пользователь',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    ...when,
    ...extra,
  })
  return [
    mk('Встреча с командой', plus(24), { icon: 'briefcase', priority: 'high', place: 'Офис, переговорная', notes: 'Обсудить детали проекта', source: 'Агент' }),
    mk('Позвонить клиенту', plus(95), { icon: 'phone', priority: 'medium', place: 'Онлайн', notes: 'Обсудить детали проекта, уточнить сроки и бюджет.' }),
    mk('Тренировка', plus(168), { icon: 'dumbbell', priority: 'low', place: 'Фитнес-клуб', repeat: 'weekly' }),
    mk('Прочитать книгу', plus(260), { icon: 'book', priority: 'low', notes: 'Глава 5', repeat: 'daily' }),
    mk('Позвонить маме', plus(420), { icon: 'home', priority: 'high' }),
    mk('Проверить статус заказа', at(0, '17:10'), { icon: 'box', done: true, notes: 'Проверить статус и отправить клиенту' }),
    mk('Оплатить подписку', at(1, '10:00'), { icon: 'card', priority: 'medium', repeat: 'monthly' }),
    mk('Записаться к врачу', at(1, '12:30'), { icon: 'pill', priority: 'high', place: 'Клиника' }),
    mk('Купить продукты на неделю', at(2, '18:00'), { icon: 'cart', priority: 'low' }),
    mk('Отправить отчёт', at(3, '09:30'), { icon: 'briefcase', priority: 'high', notes: 'Квартальная сводка' }),
  ]
}

function seedAccount(name = 'Александр'): Account {
  return {
    id: uid('acc'),
    name,
    createdAt: Date.now(),
    locked: false,
    reminders: seedReminders(),
    notifications: [],
    logs: [
      {
        id: uid('log'),
        agent: 'Memento Orchestrator',
        model: 'Claude Sonnet 5',
        status: 'ok',
        text: 'Приложение запущено. Собираю контекст: 10 напоминаний, 3 агента готовы к работе.',
        at: Date.now() - 60000,
      },
    ],
    chat: [
      {
        id: uid('msg'),
        role: 'agent',
        agent: 'Memento Orchestrator · Claude Sonnet 5',
        text: 'Привет, Александр. Я отвечаю за пайплайн агентов: разбираю команду, планирую, применяю изменения и проверяю результат. Напиши обычной фразой — например «напомни завтра в 9 встреча с командой».',
        at: Date.now() - 60000,
      },
    ],
    settings: { ...DEFAULT_SETTINGS, userName: name },
    connectors: CONNECTORS_SEED.map((c) => ({ ...c, fields: c.fields.map((f) => ({ ...f })) })),
  }
}

function loadDB(): DB {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as DB
      if (parsed?.accounts?.length) return parsed
    }
  } catch {
    /* повреждённое хранилище — стартуем с сида */
  }
  const acc = seedAccount()
  return { accounts: [acc], activeId: acc.id }
}

/* ------------------------------ Провайдер -------------------------------- */

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const [db, setDb] = useState<DB>(() => loadDB())
  const [screen, setScreen] = useState<Screen>('home')
  const [toasts, setToasts] = useState<Toast[]>([])
  const [thinking, setThinking] = useState(false)
  const [agentStatus, setAgentStatus] = useState<Record<string, 'idle' | 'run' | 'ok' | 'warn' | 'err'>>(
    Object.fromEntries(AGENTS.map((a) => [a.id, 'idle'])),
  )
  const [now, setNow] = useState(Date.now())
  const dbRef = useRef(db)
  dbRef.current = db

  const account = useMemo(
    () => db.accounts.find((a) => a.id === db.activeId) || db.accounts[0],
    [db],
  )

  const patch = useCallback((fn: (a: Account) => Account) => {
    setDb((prev) => ({ ...prev, accounts: prev.accounts.map((a) => (a.id === prev.activeId ? fn(a) : a)) }))
  }, [])

  /* --------------------------- Персистентность --------------------------- */
  useEffect(() => {
    const t = setTimeout(() => {
      try {
        localStorage.setItem(KEY, JSON.stringify(db))
      } catch {
        /* хранилище переполнено */
      }
    }, 250)
    return () => clearTimeout(t)
  }, [db])

  const toast = useCallback((text: string, kind: Toast['kind'] = 'info') => {
    const id = uid('t')
    setToasts((prev) => [...prev.slice(-3), { id, text, kind }])
    setTimeout(() => setToasts((prev) => prev.filter((x) => x.id !== id)), 5200)
  }, [])

  const navigate = useCallback((s: Screen) => setScreen(s), [])

  /* ------------------------------- Оповещения ---------------------------- */
  const fire = useCallback((rem: Reminder) => {
    const acc = dbRef.current.accounts.find((x) => x.id === dbRef.current.activeId)!
    const title = rem.title
    const kind = rem.priority === 'high' ? 'Высокий приоритет' : 'Напоминание'
    patch((a) => {
      const others = a.reminders.filter((x) => x.id !== rem.id)
      const nextDate = rem.repeat !== 'none' ? nextOccurrenceISO(rem.date, rem.time, rem.repeat) : null
      const updated: Reminder = nextDate
        ? { ...rem, date: nextDate, firedAt: null, snoozedUntil: null }
        : { ...rem, firedAt: Date.now() }
      const notif: NotificationItem = {
        id: uid('n'),
        reminderId: rem.id,
        title,
        body: rem.place ? `${rem.place}${rem.notes ? ` · ${rem.notes}` : ''}` : rem.notes || kind,
        at: Date.now(),
        kind: 'due',
        read: false,
      }
      return { ...a, reminders: [...others, updated], notifications: [notif, ...a.notifications].slice(0, 40) }
    })
    toast(`⏰ ${title}`, 'warn')
    beep(acc.settings.soundEnabled)
    if (acc.settings.notificationsEnabled) {
      const browser = acc.connectors.find((c) => c.id === 'browser')
      try {
        if (browser?.connected && 'Notification' in window && Notification.permission === 'granted') {
          new Notification(`Memento · ${title}`, { body: rem.notes || rem.place || 'Пора выполнить напоминание' })
        }
      } catch {
        /* уведомления недоступны */
      }
    }
  }, [patch, toast])

  /* --------------------------- Планировщик времени ----------------------- */
  useEffect(() => {
    const int = setInterval(() => {
      const t = Date.now()
      setNow(t)
      const acc = dbRef.current.accounts.find((x) => x.id === dbRef.current.activeId)
      if (!acc) return
      for (const r of acc.reminders) {
        if (r.done || r.firedAt) continue
        const due = r.snoozedUntil ?? reminderDate(r)
        if (due <= t) {
          if (t - due < 3 * 3600000) fire(r)
          else patch((a) => ({
            ...a,
            reminders: a.reminders.map((x) => (x.id === r.id ? { ...x, firedAt: t } : x)),
          }))
          break
        }
      }
    }, 1000)
    return () => clearInterval(int)
  }, [fire, patch])

  /* --------------------------- CRUD напоминаний -------------------------- */
  const addReminder = useCallback((r: Partial<Reminder>) => {
    const id = uid('rem')
    patch((a) => ({
      ...a,
      reminders: [
        ...a.reminders,
        {
          id,
          title: r.title || 'Новое напоминание',
          notes: r.notes || '',
          icon: r.icon || 'bell',
          date: r.date || todayISO(),
          time: r.time || '12:00',
          repeat: (r.repeat || 'none') as Repeat,
          priority: (r.priority || 'medium') as Priority,
          place: r.place || '',
          leadMinutes: r.leadMinutes ?? 10,
          done: false,
          firedAt: null,
          snoozedUntil: null,
          source: r.source || 'Пользователь',
          createdAt: Date.now(),
          updatedAt: Date.now(),
        },
      ],
    }))
    return id
  }, [patch])

  const updateReminder = useCallback((id: string, p: Partial<Reminder>) => {
    patch((a) => ({
      ...a,
      reminders: a.reminders.map((r) => (r.id === id ? { ...r, ...p, updatedAt: Date.now() } : r)),
    }))
  }, [patch])

  const deleteReminder = useCallback((id: string) => {
    patch((a) => ({ ...a, reminders: a.reminders.filter((r) => r.id !== id) }))
    toast('Напоминание удалено', 'info')
  }, [patch, toast])

  const toggleDone = useCallback((id: string) => {
    patch((a) => {
      const rem = a.reminders.find((r) => r.id === id)
      if (!rem) return a
      const done = !rem.done
      const nextDate = done && rem.repeat !== 'none' ? nextOccurrenceISO(rem.date, rem.time, rem.repeat) : null
      const updated: Reminder = nextDate
        ? { ...rem, done: false, date: nextDate, firedAt: null }
        : { ...rem, done, firedAt: done ? rem.firedAt : null }
      return {
        ...a,
        reminders: a.reminders.map((r) => (r.id === id ? updated : r)),
        notifications: done
          ? [{ id: uid('n'), reminderId: id, title: rem.title, body: 'Отмечено выполненным', at: Date.now(), kind: 'done' as const, read: false }, ...a.notifications].slice(0, 40)
          : a.notifications,
      }
    })
  }, [patch])

  const snooze = useCallback((id: string, minutes: number) => {
    patch((a) => ({
      ...a,
      reminders: a.reminders.map((r) =>
        r.id === id ? { ...r, snoozedUntil: Date.now() + minutes * 60000, firedAt: null } : r,
      ),
      notifications: [
        { id: uid('n'), reminderId: id, title: a.reminders.find((r) => r.id === id)?.title || '', body: `Отложено на ${minutes} мин`, at: Date.now(), kind: 'snooze' as const, read: false },
        ...a.notifications,
      ].slice(0, 40),
    }))
    toast(`Отложено на ${minutes} мин`, 'info')
  }, [patch, toast])

  const clearNotifications = useCallback(() => patch((a) => ({ ...a, notifications: [] })), [patch])
  const markNotificationsRead = useCallback(
    () => patch((a) => ({ ...a, notifications: a.notifications.map((n) => ({ ...n, read: true })) })),
    [patch],
  )

  const setSettings = useCallback((p: Partial<Settings>) => patch((a) => ({
    ...a,
    name: typeof p.userName === 'string' && p.userName.trim() ? p.userName : a.name,
    settings: { ...a.settings, ...p },
  })), [patch])

  const setConnector = useCallback((id: string, p: Partial<Connector>) => {
    patch((a) => ({ ...a, connectors: a.connectors.map((c) => (c.id === id ? { ...c, ...p } : c)) }))
  }, [patch])

  const setConnectorField = useCallback((id: string, key: string, value: string) => {
    patch((a) => ({
      ...a,
      connectors: a.connectors.map((c) =>
        c.id === id ? { ...c, fields: c.fields.map((f) => (f.key === key ? { ...f, value } : f)) } : c,
      ),
    }))
  }, [patch])

  const addAccount = useCallback((name: string) => {
    const acc = seedAccount(name)
    acc.reminders = []
    acc.chat = []
    acc.logs = []
    setDb((prev) => ({ accounts: [...prev.accounts, acc], activeId: acc.id }))
    toast(`Профиль «${name}» создан`, 'ok')
  }, [toast])

  const switchAccount = useCallback((id: string) => {
    setDb((prev) => ({ ...prev, activeId: id }))
    setScreen('home')
  }, [])

  const resetDemo = useCallback(() => {
    const acc = seedAccount(dbRef.current.accounts.find((a) => a.id === dbRef.current.activeId)?.name || 'Александр')
    setDb((prev) => ({ ...prev, accounts: prev.accounts.map((a) => (a.id === prev.activeId ? { ...acc, id: a.id } : a)) }))
    setScreen('home')
    toast('Демо-данные восстановлены', 'ok')
  }, [toast])

  /* ----------------------------- Логи и чат ------------------------------ */
  const pushLog = useCallback((entry: Omit<LogEntry, 'id' | 'at'>) => {
    const id = uid('log')
    patch((a) => ({
      ...a,
      logs: [...a.logs, { ...entry, id, at: Date.now() }].slice(-300),
    }))
    return id
  }, [patch])

  const pushChat = useCallback((msg: Omit<ChatMessage, 'id' | 'at'>) => {
    patch((a) => ({ ...a, chat: [...a.chat, { ...msg, id: uid('msg'), at: Date.now() }].slice(-200) }))
  }, [patch])

  const setStatus = useCallback((agentId: string, s: 'idle' | 'run' | 'ok' | 'warn' | 'err') => {
    setAgentStatus((prev) => ({ ...prev, [agentId]: s }))
  }, [])

  const mkCtx = useCallback((): AgentContext => {
    const acc = dbRef.current.accounts.find((a) => a.id === dbRef.current.activeId)!
    return {
      base: { reminders: acc.reminders, settings: acc.settings, now: Date.now() },
      log: pushLog,
      status: setStatus,
      chat: pushChat,
      commit: (reminders, settings, results) => {
        const baseIds = new Set(acc.reminders.map((r) => r.id))
        patch((a) => {
          const next = a.reminders.filter((r) => !baseIds.has(r.id) || reminders.some((x) => x.id === r.id))
          const byId = new Map(next.map((r) => [r.id, r]))
          for (const r of reminders) byId.set(r.id, r)
          return { ...a, reminders: [...byId.values()], settings: { ...a.settings, ...settings } }
        })
        const nav = results.find((r) => r.ok && r.op.tool === 'navigate')
        if (nav?.op.tool === 'navigate') setScreen(nav.op.args.screen)
      },
      toast,
      delay: (ms: number) => new Promise((res) => setTimeout(res, ms)),
    }
  }, [patch, pushChat, pushLog, setStatus, toast])

  const ask = useCallback(async (text: string) => {
    const clean = text.trim()
    if (!clean) return
    if (dbRef.current.accounts.find((a) => a.id === dbRef.current.activeId)?.locked) return
    pushChat({ role: 'user', text: clean })
    setThinking(true)
    setAgentStatus(Object.fromEntries(AGENTS.map((a) => [a.id, 'idle'])))
    try {
      await runPipeline(clean, mkCtx())
    } catch (e: any) {
      toast(`Агент упал: ${e?.message || e}`, 'err')
      setAgentStatus((prev) => ({ ...prev, orchestrator: 'err' }))
    } finally {
      setThinking(false)
    }
  }, [mkCtx, pushChat, toast])

  const runTask = useCallback(async (task: 'audit' | 'reschedule' | 'demo') => {
    setThinking(true)
    setAgentStatus(Object.fromEntries(AGENTS.map((a) => [a.id, 'idle'])))
    try {
      if (task === 'audit') await runAudit(mkCtx())
      if (task === 'reschedule') await runReschedule(mkCtx())
      if (task === 'demo') {
        for (const prompt of DEMO_PROMPTS) {
          await runPipeline(prompt, mkCtx())
          await new Promise((r) => setTimeout(r, 450))
        }
      }
    } catch (e: any) {
      toast(`Задача не выполнена: ${e?.message || e}`, 'err')
    } finally {
      setThinking(false)
    }
  }, [mkCtx, toast])

  const lock = useCallback(() => {
    patch((a) => ({ ...a, locked: true }))
    setScreen('home')
  }, [patch])
  const unlock = useCallback(() => patch((a) => ({ ...a, locked: false })), [patch])

  const value: Store = {
    account,
    accounts: db.accounts,
    screen,
    toasts,
    thinking,
    agentStatus,
    navigate,
    lock,
    unlock,
    addReminder,
    updateReminder,
    deleteReminder,
    toggleDone,
    snooze,
    clearNotifications,
    markNotificationsRead,
    setSettings,
    setConnector,
    setConnectorField,
    addAccount,
    switchAccount,
    resetDemo,
    ask,
    runTask,
    toast,
    now,
  }

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useStore() {
  const c = useContext(Ctx)
  if (!c) throw new Error('StoreProvider отсутствует')
  return c
}

export function useAgentLabel() {
  return AGENTS
}
