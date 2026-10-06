import type { Connector, Op, Settings, WidgetDef } from '../types'


export interface AgentDef {
  id: string
  name: string
  role: string
  model: string
  icon: string
  chief?: boolean
}

/** Пайплайн агентов: рабочие агенты на Claude Sonnet 5, главный — оркестратор с проверкой. */
export const AGENTS: AgentDef[] = [
  {
    id: 'orchestrator',
    name: 'Memento Orchestrator',
    role: 'Главный агент: ставит цель, распределяет работу и проверяет всё до конца',
    model: 'Claude Sonnet 5',
    icon: 'shield',
    chief: true,
  },
  {
    id: 'parser',
    name: 'NLU · Парсер намерения',
    role: 'Разбирает фразу на смысл, дату, время, повтор и приоритет',
    model: 'Claude Sonnet 5',
    icon: 'search',
  },
  {
    id: 'planner',
    name: 'Планировщик',
    role: 'Считает календарь, разрешает конфликты и собирает вызовы инструментов',
    model: 'Claude Sonnet 5',
    icon: 'calendar',
  },
  {
    id: 'editor',
    name: 'Редактор напоминаний',
    role: 'Вызывает инструменты приложения и реально меняет данные',
    model: 'Claude Sonnet 5',
    icon: 'sparkles',
  },
  {
    id: 'validator',
    name: 'Валидатор (QA)',
    role: 'Перечитывает состояние приложения и подтверждает каждую операцию',
    model: 'Claude Sonnet 5',
    icon: 'check',
  },
]

export interface ToolDef {
  name: string
  title: string
  desc: string
  schema: Record<string, string>
}

/** Контракт инструментов — то, что агенты могут делать с приложением (MCP-совместимо). */
export const TOOLS: ToolDef[] = [
  {
    name: 'create_reminder',
    title: 'Создать напоминание',
    desc: 'Добавляет напоминание с датой, временем, повтором и приоритетом',
    schema: { title: 'string', date: 'YYYY-MM-DD', time: 'HH:MM', repeat: 'none|daily|weekdays|weekly|monthly', priority: 'high|medium|low', notes: 'string', place: 'string', leadMinutes: 'number' },
  },
  {
    name: 'update_reminder',
    title: 'Изменить напоминание',
    desc: 'Меняет любые поля существующего напоминания',
    schema: { id: 'string', patch: 'Partial<Reminder>' },
  },
  {
    name: 'complete_reminder',
    title: 'Отметить выполненным',
    desc: 'Переключает статус выполнения',
    schema: { id: 'string', done: 'boolean' },
  },
  {
    name: 'snooze_reminder',
    title: 'Отложить',
    desc: 'Откладывает напоминание на N минут',
    schema: { id: 'string', minutes: 'number' },
  },
  {
    name: 'delete_reminder',
    title: 'Удалить',
    desc: 'Удаляет напоминание из базы',
    schema: { id: 'string' },
  },
  {
    name: 'query_reminders',
    title: 'Прочитать состояние',
    desc: 'Возвращает список напоминаний по фильтру — используется для проверки',
    schema: { filter: 'today|week|all|done|overdue|upcoming' },
  },
  {
    name: 'set_widget_layout',
    title: 'Настроить виджеты',
    desc: 'Меняет размер виджета «Сегодня» и состав виджетов главного экрана',
    schema: { layout: '2x2|4x2|4x4', activeWidgets: 'string[]' },
  },
  {
    name: 'set_setting',
    title: 'Изменить настройки',
    desc: 'Тема, акцент, звук, уведомления, имя пользователя, режим проверки агентом',
    schema: { key: 'keyof Settings', value: 'any' },
  },
  {
    name: 'navigate',
    title: 'Навигация',
    desc: 'Открывает нужный экран приложения',
    schema: { screen: 'home|widgets|calendar|agents|connectors|profile|notifications' },
  },
]

export const DEFAULT_WIDGETS: WidgetDef[] = [
  { id: 'today', title: 'Сегодня', caption: 'Напоминания на сегодня · размер 2×2 / 4×2 / 4×4', size: '4x2' },
  { id: 'next', title: 'Следующее', caption: 'Ближайшее напоминание с таймером', size: '2x2' },
  { id: 'progress', title: 'Прогресс дня', caption: 'Сколько выполнено из запланированного', size: '2x2' },
  { id: 'week', title: 'Ближайшая неделя', caption: 'Загрузка по дням', size: '4x2' },
  { id: 'schedule', title: 'Расписание на день', caption: 'Таймлайн дня с деталями', size: '4x4' },
  { id: 'agent', title: 'Агент', caption: 'Что только что сделал ИИ-агент', size: '4x2' },
]

export const DEFAULT_SETTINGS: Settings = {
  userName: 'Александр',
  widgetLayout: '4x2',
  activeWidgets: ['today', 'next', 'progress'],
  theme: 'dark',
  accent: '#8b93a7',
  notificationsEnabled: true,
  soundEnabled: true,
  verification: true,
  model: 'Claude Sonnet 5',
  autoLock: false,
}

export const CONNECTORS_SEED: Connector[] = [
  {
    id: 'claude',
    name: 'Anthropic Claude API',
    desc: 'Реальный Claude за пайплайном агентов. Без ключа работает встроенный локальный движок.',
    icon: 'sparkles',
    connected: false,
    fields: [{ key: 'apiKey', label: 'API-ключ', type: 'password', value: '', placeholder: 'sk-ant-…' }],
    test: 'claude',
  },
  {
    id: 'calendar',
    name: 'Google / Apple Календарь',
    desc: 'Экспорт всех напоминаний в .ics и импорт событий обратно.',
    icon: 'calendar',
    connected: true,
    fields: [],
    test: 'ics',
  },
  {
    id: 'telegram',
    name: 'Telegram-бот',
    desc: 'Дублирует напоминания в чат через Bot API.',
    icon: 'send',
    connected: false,
    fields: [
      { key: 'token', label: 'Токен бота', type: 'password', value: '', placeholder: '123456:ABC-DEF…' },
      { key: 'chatId', label: 'Chat ID', type: 'text', value: '', placeholder: '123456789' },
    ],
    test: 'telegram',
  },
  {
    id: 'webhook',
    name: 'Webhook / n8n',
    desc: 'POST-события при создании, изменении и срабатывании напоминаний.',
    icon: 'globe',
    connected: false,
    fields: [{ key: 'url', label: 'URL', type: 'text', value: '', placeholder: 'https://example.com/hook' }],
    test: 'webhook',
  },
  {
    id: 'notion',
    name: 'Notion / Markdown',
    desc: 'Выгрузка базы напоминаний в Markdown-файл.',
    icon: 'note',
    connected: true,
    fields: [],
    test: 'markdown',
  },
  {
    id: 'mcp',
    name: 'Memento MCP-шина',
    desc: 'Локальный сервер инструментов: агенты вызывают приложение по контракту.',
    icon: 'database',
    connected: true,
    fields: [],
    test: 'mcp',
  },
  {
    id: 'browser',
    name: 'Системные уведомления браузера',
    desc: 'Пуш-уведомления, когда напоминание срабатывает, даже если вкладка свёрнута.',
    icon: 'bell',
    connected: false,
    fields: [],
    test: 'browserNotify',
  },
]

/** Снимок приложения, отданный агентам (то, что они "видят"). */
export interface AgentWorld {
  reminders: import('../types').Reminder[]
  settings: Settings
  now: number
}

export interface AgentContext {
  base: AgentWorld
  log: (entry: { agent: string; model: string; status: 'run' | 'ok' | 'warn' | 'err'; text: string }) => string
  status: (agentId: string, s: 'idle' | 'run' | 'ok' | 'warn' | 'err') => void
  chat: (msg: { role: 'agent' | 'system'; agent?: string; text: string; logIds?: string[] }) => void
  commit: (reminders: import('../types').Reminder[], settings: Settings, results: import('../types').OpResult[]) => void
  toast: (text: string, kind?: 'ok' | 'warn' | 'err' | 'info') => void
  delay: (ms: number) => Promise<void>
}

export type AgentOp = Op
