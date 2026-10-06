export type Priority = 'high' | 'medium' | 'low'
export type Repeat = 'none' | 'daily' | 'weekdays' | 'weekly' | 'monthly'
export type Screen =
  | 'home'
  | 'widgets'
  | 'calendar'
  | 'agents'
  | 'connectors'
  | 'profile'
  | 'notifications'

export interface Reminder {
  id: string
  title: string
  notes: string
  icon: string
  date: string // YYYY-MM-DD
  time: string // HH:MM
  repeat: Repeat
  priority: Priority
  place: string
  leadMinutes: number
  done: boolean
  firedAt: number | null
  snoozedUntil: number | null
  source: string
  createdAt: number
  updatedAt: number
}

export interface NotificationItem {
  id: string
  reminderId: string
  title: string
  body: string
  at: number
  kind: 'due' | 'snooze' | 'done' | 'agent'
  read: boolean
}

export interface LogEntry {
  id: string
  agent: string
  model: string
  status: 'run' | 'ok' | 'warn' | 'err'
  text: string
  at: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'agent' | 'system'
  agent?: string
  text: string
  at: number
  logIds?: string[]
}

export interface ConnectorField {
  key: string
  label: string
  type: 'text' | 'password'
  value: string
  placeholder?: string
}

export interface Connector {
  id: string
  name: string
  desc: string
  icon: string
  connected: boolean
  fields: ConnectorField[]
  test?: string // url or special handler id
}

export interface Settings {
  userName: string
  widgetLayout: '2x2' | '4x2' | '4x4'
  activeWidgets: string[]
  theme: 'dark' | 'black'
  accent: string
  notificationsEnabled: boolean
  soundEnabled: boolean
  verification: boolean
  model: string
  autoLock: boolean
}

export interface WidgetDef {
  id: string
  title: string
  caption: string
  size: '2x2' | '4x2' | '4x4'
}

export type Op =
  | { tool: 'create_reminder'; args: Partial<Reminder> & { title: string } }
  | { tool: 'update_reminder'; args: { id: string; patch: Partial<Reminder> } }
  | { tool: 'delete_reminder'; args: { id: string } }
  | { tool: 'complete_reminder'; args: { id: string; done?: boolean } }
  | { tool: 'snooze_reminder'; args: { id: string; minutes: number } }
  | { tool: 'navigate'; args: { screen: Screen } }
  | { tool: 'set_setting'; args: { key: keyof Settings; value: any } }
  | { tool: 'set_widget_layout'; args: { layout: Settings['widgetLayout']; activeWidgets?: string[] } }
  | { tool: 'query_reminders'; args: { filter: 'today' | 'all' | 'done' | 'overdue' | 'upcoming' | 'week' } }

export interface OpResult {
  op: Op
  ok: boolean
  message: string
}
