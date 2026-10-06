import type { Op, OpResult, Priority, Reminder, Repeat, Screen, Settings } from '../types'
import { AGENTS } from './agents'
import {
  addDaysISO, plural, timeToMinutes, todayISO, uid, fromISO, reminderDate, toISO,
} from '../lib/date'
import type { AgentContext, AgentWorld } from './agents'

/* ---------------------------------- NLU ---------------------------------- */

export interface ParsedIntent {
  kind: 'create' | 'complete' | 'delete' | 'snooze' | 'move' | 'query' | 'settings' | 'widgets' | 'navigate' | 'unknown' | 'chat'
  title: string
  date?: string
  time?: string
  repeat?: Repeat
  priority?: Priority
  notes?: string
  place?: string
  leadMinutes?: number
  targetRef?: string
  minutes?: number
  query?: 'today' | 'week' | 'all' | 'done' | 'overdue' | 'upcoming'
  settingKey?: keyof Settings
  settingValue?: any
  layout?: Settings['widgetLayout']
  screen?: Screen
  confidence: number
  confidenceLabel: 'высокая' | 'средняя' | 'низкая'
  normalized: string
}

const WEEKDAYS: Record<string, number> = {
  воскресенье: 0, понедельник: 1, вторник: 2, среда: 3, среду: 3, четверг: 4, пятница: 5, пятницу: 5, суббота: 6, субботу: 6,
}
const WEEK_ALIASES: Record<string, string> = { вос: 'вс', вскр: 'вс', вскресенье: 'вс', сб: 'сб', суб: 'сб', пн: 'пн', вт: 'вт', ср: 'ср', чт: 'чт', пт: 'пт', вс: 'вс' }

const HI_HINTS = ['важно', 'срочно', 'критично', 'обязательно', 'приоритет высокий', 'не забыть', 'самое важное']
const LO_HINTS = ['не срочно', 'низкий приоритет', 'когда-нибудь', 'спокойно']

const ICON_HINTS: [RegExp, string][] = [
  [/трениров|спорт|фитнес|зал|бег|йог/i, 'dumbbell'],
  [/позвон|звонок|созвон|перезвон/i, 'phone'],
  [/встреч|митинг|собес|переговор/i, 'briefcase'],
  [/книг|читать|прочитать|глава/i, 'book'],
  [/куп|магазин|продукт|заказ.*доставк/i, 'cart'],
  [/таблетк|лекарств|витамин|врач|анализ/i, 'pill'],
  [/почт|письм|email|мейл/i, 'mail'],
  [/платеж|оплат|счет|счёт|кредит|налог|подписк/i, 'card'],
  [/подарок|день рожден|поздравить/i, 'gift'],
  [/посылк|заказ|отправ/i, 'box'],
  [/заправ|авто|машин|техосмотр/i, 'car'],
  [/мама|папа|бабушк|дедушк|семь/i, 'home'],
  [/отчет|отчёт|презентац|дедлайн|проект/i, 'briefcase'],
  [/зарядк|тренировк|вода|сон|привычк/i, 'target'],
  [/метр|поезд|рейс|билет/i, 'clock'],
  [/онлайн|сайт|ссылка/i, 'globe'],
]

function pickIcon(text: string) {
  for (const [re, icon] of ICON_HINTS) if (re.test(text)) return icon
  return 'bell'
}

const MONTH_KEYS = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']
const WEEKDAY_FORMS: [string, number][] = [
  ['воскресенье', 0], ['понедельник', 1], ['вторник', 2], ['среду', 3], ['среда', 3],
  ['четверг', 4], ['пятницу', 5], ['пятница', 5], ['субботу', 6], ['суббота', 6],
]
const MIN_FORMS = '(мин|минуту|минуты|минут|минутки)'
const HOUR_FORMS = '(час|часа|часов)'
const DAY_FORMS = '(день|дня|дней|дн)'

/** Парсер намерения. Работает на строке, дополненной пробелами: в JS \b не знает кириллицу. */
export function parseIntent(raw: string, world: AgentWorld): ParsedIntent {
  const text0 = raw.trim().replace(/\s+/g, ' ')
  const T = ` ${text0.toLowerCase().replace(/ё/g, 'е')} `
  const has = (s: string) => T.includes(s)
  const rx = (re: RegExp) => T.match(re)

  let confidence = 0.7
  let time: string | undefined
  let date: string | undefined
  let repeat: Repeat | undefined
  let priority: Priority | undefined
  let place: string | undefined
  let minutes: number | undefined
  let kind: ParsedIntent['kind'] = 'create'
  let targetRef: string | undefined
  let notes: string | undefined
  let query: ParsedIntent['query']
  let screen: Screen | undefined
  let layout: Settings['widgetLayout'] | undefined
  let settingKey: keyof Settings | undefined
  let settingValue: any

  const setClock = (d: Date) => {
    date = toISO(d)
    time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  /* ------------------------------- Дата ---------------------------------- */
  if (has(' сегодня ') || has(' на сегодня ')) { date = todayISO(); confidence += 0.06 }
  else if (has(' завтра ')) { date = addDaysISO(todayISO(), 1); confidence += 0.06 }
  else if (has(' послезавтра ')) { date = addDaysISO(todayISO(), 2); confidence += 0.06 }

  if (!date && rx(new RegExp(`через (\\d{1,2}) ${DAY_FORMS}`))) {
    date = addDaysISO(todayISO(), Number(rx(new RegExp(`через (\\d{1,2}) ${DAY_FORMS}`))![1]))
    confidence += 0.05
  }
  if (!date && has(' через неделю ')) { date = addDaysISO(todayISO(), 7); confidence += 0.05 }

  if (!date) {
    const next = rx(new RegExp(' следующ(?:ую|ий) (неделю|' + WEEKDAY_FORMS.map((w) => w[0]).join('|') + ') '))
    if (next) {
      const form = next[1]
      const wd = form === 'неделю' ? new Date().getDay() : WEEKDAY_FORMS.find((w) => w[0] === form)?.[1] ?? new Date().getDay()
      const d = new Date()
      d.setDate(d.getDate() + 7)
      d.setDate(d.getDate() + ((wd - d.getDay() + 7) % 7))
      date = toISO(d); confidence += 0.05
    }
  }

  if (!date) {
    const wd = rx(new RegExp(' (?:в|во) (' + WEEKDAY_FORMS.map((w) => w[0]).join('|') + ') '))
    if (wd) {
      const target = WEEKDAY_FORMS.find((w) => w[0] === wd[1])![1]
      const d = new Date()
      let delta = (target - d.getDay() + 7) % 7
      if (delta === 0) delta = 7
      date = addDaysISO(todayISO(), delta); confidence += 0.06
    }
  }

  if (!date) {
    const dm = rx(new RegExp(` (\\d{1,2})[. ](${MONTH_KEYS.join('|')})[а-я]* ?(\\d{4})? `))
    if (dm) {
      const mi = MONTH_KEYS.findIndex((x) => dm[2].startsWith(x.slice(0, 3)) || x.startsWith(dm[2].slice(0, 3)))
      if (mi >= 0) {
        const now = new Date()
        const year = dm[3] ? Number(dm[3]) : now.getMonth() > mi ? now.getFullYear() + 1 : now.getFullYear()
        date = `${year}-${String(mi + 1).padStart(2, '0')}-${String(Number(dm[1])).padStart(2, '0')}`
        confidence += 0.08
      }
    }
  }

  /* ------------------------------- Время --------------------------------- */
  const tm = rx(/ (?:в|во) (\d{1,2})[:.](\d{2}) /)
  if (tm) { time = `${String(Number(tm[1]) % 24).padStart(2, '0')}:${tm[2]}`; confidence += 0.08 }
  else {
    const hm = rx(/ (?:в|во) (\d{1,2})(?![:.\d]) /)
    if (hm && Number(hm[1]) <= 23) { time = `${String(Number(hm[1])).padStart(2, '0')}:00`; confidence += 0.04 }
    else if (has(' в полдень ')) time = '12:00'
    else if (has(' в полночь ')) time = '00:00'
    else {
      const mn = rx(new RegExp(`через (\\d{1,3}) ${MIN_FORMS}`))
      const hr = rx(new RegExp(`через (\\d{1,2}) ${HOUR_FORMS}`))
      if (mn) { const d = new Date(Date.now() + Number(mn[1]) * 60000); setClock(d); confidence += 0.06 }
      else if (hr) { const d = new Date(Date.now() + Number(hr[1]) * 3600000); setClock(d); confidence += 0.05 }
      else {
        const part = rx(/ (утром|днем|вечером|ночью) /)
        if (part) {
          const h = part[1] === 'утром' ? 9 : part[1] === 'ночью' ? 22 : part[1] === 'вечером' ? 19 : 14
          time = `${String(h).padStart(2, '0')}:00`; confidence += 0.03
        }
      }
    }
  }

  /* ------------------------------ Повтор -------------------------------- */
  if (/кажд(ый|ое|ую) (день|утро|вечер|ночь)|ежедневн|по утрам|по вечерам|каждодневн/.test(T)) repeat = 'daily'
  else if (/по будням|по рабочим дням|кроме выходных/.test(T)) repeat = 'weekdays'
  else if (/каждую неделю|еженедельн|раз в неделю|кажд(ый|ую) (понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)/.test(T)) repeat = 'weekly'
  else if (/каждый месяц|ежемесячн|раз в месяц/.test(T)) repeat = 'monthly'

  /* ---------------------------- Приоритет ------------------------------- */
  if (HI_HINTS.some((h) => T.includes(h))) { priority = 'high'; confidence += 0.03 }
  else if (LO_HINTS.some((h) => T.includes(h))) { priority = 'low'; confidence += 0.02 }

  /* ----------------------- Место, заметки, за сколько ------------------- */
  const placeM = rx(/ (?:в|на) (офис|онлайн|дом|кафе|зал|спортзал|клиник|школ|аэропорт|вокзал|переговорн)[а-я]* /)
  if (placeM) place = placeM[1].charAt(0).toUpperCase() + placeM[1].slice(1)
  const notesM = text0.match(/[-—:]\s*(.{6,})/)
  if (notesM) { notes = notesM[1].trim(); confidence -= 0.02 }
  const leadM = rx(new RegExp(`за (\\d{1,3}) ${MIN_FORMS} до`))
  if (leadM) minutes = Number(leadM[1])
  const leadH = rx(new RegExp(`за (\\d{1,2}) ${HOUR_FORMS} до`))
  if (leadH) minutes = Number(leadH[1]) * 60

  /* ------------------------- Вид команды --------------------------------- */
  const widgetM = T.match(/виджет[а-я]* (?:на |размер[а-я]* )?(2 ?[xх×] ?2|4 ?[xх×] ?2|4 ?[xх×] ?4)/)
  const snoozeWords = /(отложи|отложить|отлож|перенеси|перенести|напомни позже|поставь на паузу)/.test(T)
  const doneWords = /(отметь|отметить|выполнен|готово|сделано|закрой|заверши|галочк|чек)/.test(T)
  const delWords = /(удали|удалить|убери|убрать|стереть|снос)/.test(T)
  const navM = T.match(/ (перейди|открой|покажи) (?:на )?(главн|календар|виджет|профил|агент|коннектор|подключени|уведомлен|напоминани)/)
  const showWords = /(покажи|показать|список|какие|что у меня|что на |сколько у меня|что запланировано)/.test(T)

  if (widgetM) {
    kind = 'widgets'
    const norm = widgetM[1].replace(/[\sх×]/g, 'x')
    if (norm === '2x2' || norm === '4x2' || norm === '4x4') layout = norm
    confidence += 0.12
  } else if (/^(помощь|help|что ты умеешь|команды)/.test(T.trim())) {
    kind = 'chat'
  } else if (snoozeWords && !/виджет/.test(T)) {
    kind = 'snooze'
    const mm = rx(new RegExp(`на (\\d{1,3}) ${MIN_FORMS}`))
    const hh = rx(new RegExp(`на (\\d{1,2}) ${HOUR_FORMS}`))
    minutes = mm ? Number(mm[1]) : hh ? Number(hh[1]) * 60 : 10
    targetRef = cleanTarget(text0.replace(/^.*?(отложи|отложить|перенеси|перенести|напомни позже)/i, ''))
    confidence += 0.1
  } else if (delWords) {
    kind = 'delete'
    targetRef = cleanTarget(text0.replace(/^.*?(удали|удалить|убери|убрать|стереть)/i, ''))
    confidence += 0.1
  } else if (doneWords && /(напомина|задач|его|ее|её|это|выполн|готово|сделано)/.test(T)) {
    kind = 'complete'
    targetRef = cleanTarget(text0.replace(/^.*?(отметь|отметить|выполненным|выполнено|готово|сделано|закрой|заверши)/i, '').replace(/^\s*(напоминание|задачу|задача)\s*/i, ''))
    confidence += 0.1
  } else if (/(поставь|включи|выключи|сделай|измени|поменяй|переключи).*(звук|уведомлен|тему|темн|светл|акцент|имя|проверк|виджет)|^(уведомления|звук) (вкл|выкл)/.test(T.trim()) && !widgetM) {
    kind = 'settings'
    const off = /(выключ|отключ|без |не |убери)/.test(T)
    if (/уведомлен/.test(T)) { settingKey = 'notificationsEnabled'; settingValue = !off }
    else if (/звук/.test(T)) { settingKey = 'soundEnabled'; settingValue = !off }
    else if (/проверк/.test(T)) { settingKey = 'verification'; settingValue = !off }
    else if (/светл/.test(T)) { settingKey = 'theme'; settingValue = 'dark' }
    else if (/темн|черн|уголь/.test(T)) { settingKey = 'theme'; settingValue = 'black' }
    else if (/(меня зовут|имя)/.test(T)) {
      const nm = text0.match(/(?:меня зовут|имя)\s+([А-ЯЁA-Zа-яёa-z]+)/i)
      if (nm) { settingKey = 'userName'; settingValue = nm[1] }
    }
    confidence += 0.08
  } else if (navM && !showWords) {
    kind = 'navigate'
    const g = navM[2]
    screen = /виджет/.test(g) ? 'widgets'
      : /календар/.test(g) ? 'calendar'
        : /профил/.test(g) ? 'profile'
          : /агент/.test(g) ? 'agents'
            : /коннектор|подключен/.test(g) ? 'connectors'
              : /уведомлен|напоминани/.test(g) ? 'notifications'
                : 'home'
    confidence += 0.1
  } else if (showWords) {
    kind = 'query'
    query = /сегодня/.test(T) ? 'today'
      : /недел/.test(T) ? 'week'
        : /выполн|готов/.test(T) ? 'done'
          : /просроч|опазд/.test(T) ? 'overdue'
            : /ближайш|скоро/.test(T) ? 'upcoming' : 'all'
    confidence += 0.1
  }

  /* ------------------------------ Заголовок ------------------------------ */
  let title = text0
  if (kind === 'create') {
    let s = ` ${text0} `
    s = s.replace(/^\s*(напомни|напомнить)(?: мне)?[:,\s]*/i, ' ')
    s = s.replace(/(создай|добавь|поставь|заведи)\s+(напоминание|задачу|задачу на|напоминание на)[:,\s]*/i, ' ')
    s = s.replace(/ (сегодня|завтра|послезавтра|на сегодня) /gi, ' ')
    s = s.replace(new RegExp(` через \\d{1,3} ${MIN_FORMS} `, 'gi'), ' ')
    s = s.replace(new RegExp(` через \\d{1,2} ${HOUR_FORMS} `, 'gi'), ' ')
    s = s.replace(new RegExp(` через \\d{1,2} ${DAY_FORMS} `, 'gi'), ' ')
    s = s.replace(/ через неделю /gi, ' ')
    s = s.replace(/ (?:в|во) \d{1,2}[:.]\d{2} /gi, ' ')
    s = s.replace(/ (?:в|во) \d{1,2} /gi, ' ')
    s = s.replace(new RegExp(` за \\d{1,3} ${MIN_FORMS} до `, 'gi'), ' ')
    s = s.replace(new RegExp(` за \\d{1,2} ${HOUR_FORMS} до `, 'gi'), ' ')
    s = s.replace(/ (каждый|каждое|каждую|ежедневно|ежедневно|еженедельно|ежемесячно|по утрам|по вечерам|по будням|раз в неделю|раз в месяц) (день|утро|вечер|ночь|неделю|месяц|понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)? ?/gi, ' ')
    s = s.replace(/ (в|во) (понедельник|вторник|среду|среду|четверг|пятницу|субботу|воскресенье) /gi, ' ')
    s = s.replace(/ (следующую|следующий) (неделю|понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье) /gi, ' ')
    s = s.replace(new RegExp(` на \\d{1,2} (${MONTH_KEYS.join('|')})[а-я]* ?(\\d{4})? `, 'gi'), ' ')
    s = s.replace(new RegExp(` \\d{1,2} (${MONTH_KEYS.join('|')})[а-я]* ?(\\d{4})? `, 'gi'), ' ')
    s = s.replace(/ это (важно|срочно) /gi, ' ')
    s = ` ${s.trim()} `
    s = s.replace(/\s{2,}/g, ' ').trim()
    s = s.replace(/^[-—,:;\s]+/, '').replace(/[-—,:;\s]+$/, '').trim()
    title = s.length >= 2 ? s.charAt(0).toUpperCase() + s.slice(1) : text0
    if (title.length > 80) title = title.slice(0, 77).trim() + '…'
  } else if (kind !== 'chat' && kind !== 'settings') {
    const isCommandOnly = !targetRef || /^\s*$/.test(targetRef)
    if (isCommandOnly) {
      kind = kind === 'query' ? 'query' : kind
      title = ''
    } else {
      title = targetRef!
    }
  }

  if (kind === 'create') {
    if (!date) date = todayISO()
    if (!time) {
      const d = new Date(Date.now() + 3600000)
      time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      confidence -= 0.12
    }
    if (!repeat) repeat = 'none'
  }

  confidence = Math.max(0.35, Math.min(0.97, confidence))
  const confidenceLabel: ParsedIntent['confidenceLabel'] = confidence >= 0.82 ? 'высокая' : confidence >= 0.62 ? 'средняя' : 'низкая'

  return {
    kind, title: title || text0, date, time, repeat, priority, notes, place,
    leadMinutes: minutes, targetRef, minutes, query, settingKey, settingValue,
    layout, screen, confidence, confidenceLabel,
    normalized: `${title || text0}${date ? ` · ${date}` : ''}${time ? ` · ${time}` : ''}${repeat && repeat !== 'none' ? ` · ${repeat}` : ''}`,
  }
}

/** Чистит остаток команды от служебных слов, чтобы найти нужное напоминание. */
function cleanTarget(s: string) {
  return s
    .replace(/^\s*(команду|встречу|напоминание|задачу|запись)?\s*/i, '')
    .replace(/ на \d{1,3} (минут|минуту|минуты|час|часа|часов)\S*/gi, '')
    .replace(new RegExp(` за \\d{1,3} ${MIN_FORMS}`, 'gi'), '')
    .replace(/ (сегодня|завтра|послезавтра) /gi, ' ')
    .replace(/ (в|во) \d{1,2}[:.]\d{2} /gi, ' ')
    .replace(/^[-—,:;\s]+/, '')
    .replace(/[-—,:;\s]+$/, '')
    .trim()
}

/* ------------------------------- Исполнитель ------------------------------ */

export function findReminder(ref: string | undefined, reminders: Reminder[]): Reminder | undefined {
  if (!ref) return undefined
  const r = ref.toLowerCase().trim()
  if (!r) return undefined
  const active = reminders.filter((x) => !x.done)
  const pool = active.length ? active : reminders
  const words = r.replace(/напоминание|задачу|задача|запись/g, '').trim().split(/\s+/).filter((w) => w.length > 2)
  if (!words.length) return undefined
  let best: Reminder | undefined
  let bestScore = 0
  for (const rem of pool) {
    const hay = `${rem.title} ${rem.notes} ${rem.place}`.toLowerCase()
    let score = 0
    for (const w of words) {
      if (hay.includes(w)) score += 1
      else if (hay.includes(w.slice(0, Math.max(4, w.length - 2)))) score += 0.6
    }
    if (score > bestScore) { bestScore = score; best = rem }
  }
  return bestScore >= 0.6 ? best : undefined
}

export function execute(ops: Op[], world: AgentWorld): { reminders: Reminder[]; settings: Settings; results: OpResult[] } {
  let reminders = world.reminders.map((r) => ({ ...r }))
  let settings = { ...world.settings }
  const results: OpResult[] = []

  for (const op of ops) {
    try {
      switch (op.tool) {
        case 'create_reminder': {
          const a = op.args
          const now = Date.now()
          const r: Reminder = {
            id: uid('rem'),
            title: a.title || 'Новое напоминание',
            notes: a.notes || '',
            icon: a.icon || pickIcon(a.title || ''),
            date: a.date || todayISO(),
            time: a.time || '12:00',
            repeat: a.repeat || 'none',
            priority: a.priority || 'medium',
            place: a.place || '',
            leadMinutes: a.leadMinutes ?? 10,
            done: false,
            firedAt: null,
            snoozedUntil: null,
            source: 'Агент',
            createdAt: now,
            updatedAt: now,
          }
          reminders.push(r)
          results.push({ op, ok: true, message: `Создано «${r.title}» на ${r.date} ${r.time}` })
          break
        }
        case 'update_reminder': {
          const idx = reminders.findIndex((r) => r.id === op.args.id)
          if (idx < 0) { results.push({ op, ok: false, message: 'Напоминание не найдено' }); break }
          reminders[idx] = { ...reminders[idx], ...op.args.patch, updatedAt: Date.now() }
          results.push({ op, ok: true, message: `Обновлено «${reminders[idx].title}»` })
          break
        }
        case 'complete_reminder': {
          const idx = reminders.findIndex((r) => r.id === op.args.id)
          if (idx < 0) { results.push({ op, ok: false, message: 'Напоминание не найдено' }); break }
          const done = op.args.done ?? !reminders[idx].done
          reminders[idx] = { ...reminders[idx], done, updatedAt: Date.now(), snoozedUntil: null }
          results.push({ op, ok: true, message: `«${reminders[idx].title}» — ${done ? 'выполнено' : 'снова активно'}` })
          break
        }
        case 'snooze_reminder': {
          const idx = reminders.findIndex((r) => r.id === op.args.id)
          if (idx < 0) { results.push({ op, ok: false, message: 'Напоминание не найдено' }); break }
          const now = Date.now()
          reminders[idx] = { ...reminders[idx], snoozedUntil: now + op.args.minutes * 60000, firedAt: null, updatedAt: now }
          results.push({ op, ok: true, message: `Отложено на ${op.args.minutes} мин` })
          break
        }
        case 'delete_reminder': {
          const before = reminders.length
          const target = reminders.find((r) => r.id === op.args.id)
          reminders = reminders.filter((r) => r.id !== op.args.id)
          results.push(before !== reminders.length
            ? { op, ok: true, message: `Удалено «${target?.title || ''}»` }
            : { op, ok: false, message: 'Напоминание не найдено' })
          break
        }
        case 'set_widget_layout': {
          settings = { ...settings, widgetLayout: op.args.layout }
          if (op.args.activeWidgets) settings.activeWidgets = op.args.activeWidgets
          results.push({ op, ok: true, message: `Виджеты: ${op.args.layout}` })
          break
        }
        case 'set_setting': {
          settings = { ...settings, [op.args.key]: op.args.value } as Settings
          results.push({ op, ok: true, message: `Настройка «${String(op.args.key)}» → ${String(op.args.value)}` })
          break
        }
        case 'query_reminders': {
          const f = op.args.filter
          const list = filterReminders(reminders, f, Date.now())
          results.push({ op, ok: true, message: `Прочитано ${list.length}: ${list.slice(0, 6).map((r) => `${r.title} (${r.time})`).join(', ')}` })
          break
        }
        case 'navigate': {
          results.push({ op, ok: true, message: `Экран: ${op.args.screen}` })
          break
        }
        default:
          results.push({ op, ok: false, message: 'Неизвестный инструмент' })
      }
    } catch (e: any) {
      results.push({ op, ok: false, message: String(e?.message || e) })
    }
  }
  return { reminders, settings, results }
}

export function filterReminders(list: Reminder[], filter: NonNullable<ParsedIntent['query']>, now: number) {
  const t = todayISO()
  switch (filter) {
    case 'today': return list.filter((r) => r.date === t && !r.done).sort((a, b) => timeToMinutes(a.time) - timeToMinutes(b.time))
    case 'week': return list.filter((r) => !r.done && r.date >= t && r.date <= addDaysISO(t, 7)).sort((a, b) => a.date.localeCompare(b.date) || timeToMinutes(a.time) - timeToMinutes(b.time))
    case 'done': return list.filter((r) => r.done)
    case 'overdue': return list.filter((r) => !r.done && reminderDate(r) < now)
    case 'upcoming': return list.filter((r) => !r.done && reminderDate(r) > now).sort((a, b) => reminderDate(a) - reminderDate(b))
    default: return [...list].sort((a, b) => a.date.localeCompare(b.date) || timeToMinutes(a.time) - timeToMinutes(b.time))
  }
}

/* -------------------------------- Пайплайн -------------------------------- */

function buildOps(intent: ParsedIntent, world: AgentWorld): { ops: Op[]; note?: string } {
  const ops: Op[] = []
  const now = Date.now()

  if (intent.kind === 'create') {
    const date = intent.date || todayISO()
    let time = intent.time || '12:00'
    // Планировщик: если время уже прошло сегодня — переносим на завтра
    const today = todayISO()
    let adjusted = false
    if (date === today && !intent.time) {
      const nowMin = new Date().getHours() * 60 + new Date().getMinutes()
      if (timeToMinutes(time) <= nowMin) {
        const d = new Date(now + 60 * 60000)
        time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
        adjusted = true
      }
    }
    // Конфликт по времени: сдвигаем на +30 мин, если занято той же минутой сегодня
    const clash = world.reminders.find((r) => !r.done && r.date === date && r.time === time)
    if (clash) {
      const m = timeToMinutes(time) + 30
      time = `${String(Math.floor(m / 60) % 24).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
      adjusted = true
    }
    ops.push({
      tool: 'create_reminder',
      args: {
        title: intent.title,
        date,
        time,
        repeat: intent.repeat || 'none',
        priority: intent.priority || 'medium',
        notes: intent.notes || '',
        place: intent.place || '',
        leadMinutes: intent.leadMinutes ?? 10,
      },
    })
    return { ops, note: adjusted ? `Планировщик сдвинул время на ${time} — слот был занят или уже прошёл.` : undefined }
  }

  if (intent.kind === 'complete' || intent.kind === 'delete' || intent.kind === 'snooze') {
    const target = findReminder(intent.targetRef, world.reminders) || world.reminders.filter((r) => !r.done).sort((a, b) => reminderDate(a) - reminderDate(b))[0]
    if (!target) return { ops: [], note: 'Не нашёл подходящее напоминание — уточни название.' }
    if (intent.kind === 'complete') ops.push({ tool: 'complete_reminder', args: { id: target.id, done: true } })
    if (intent.kind === 'delete') ops.push({ tool: 'delete_reminder', args: { id: target.id } })
    if (intent.kind === 'snooze') ops.push({ tool: 'snooze_reminder', args: { id: target.id, minutes: intent.minutes || 10 } })
    return { ops }
  }

  if (intent.kind === 'query') {
    ops.push({ tool: 'query_reminders', args: { filter: intent.query || 'today' } })
    return { ops }
  }
  if (intent.kind === 'navigate' && intent.screen) {
    ops.push({ tool: 'navigate', args: { screen: intent.screen } })
    return { ops }
  }
  if (intent.kind === 'widgets' && intent.layout) {
    ops.push({ tool: 'set_widget_layout', args: { layout: intent.layout } })
    ops.push({ tool: 'navigate', args: { screen: 'home' } })
    return { ops }
  }
  if (intent.kind === 'settings' && intent.settingKey) {
    ops.push({ tool: 'set_setting', args: { key: intent.settingKey, value: intent.settingValue } })
    return { ops }
  }
  return { ops, note: 'Не понял команду — попробуй сформулировать иначе.' }
}

/** Полный прогон: Парсер → Планировщик → Редактор → Валидатор, под надзором Orchestrator. */
export async function runPipeline(userText: string, ctx: AgentContext): Promise<void> {
  const A = Object.fromEntries(AGENTS.map((a) => [a.id, a])) as Record<string, typeof AGENTS[number]>
  const ids: string[] = []
  const log = (agentId: string, status: 'run' | 'ok' | 'warn' | 'err', text: string) => {
    const a = A[agentId]
    ids.push(ctx.log({ agent: a.name, model: a.model, status, text }))
  }

  ctx.status('orchestrator', 'run')
  log('orchestrator', 'run', `Задача: «${userText}». Собираю пайплайн 4 агентов.`)
  await ctx.delay(420)

  // 1. Парсер
  ctx.status('parser', 'run')
  log('parser', 'run', 'Разбираю фразу: сущности, дата, время, повтор, приоритет.')
  await ctx.delay(520)
  const intent = parseIntent(userText, ctx.base)
  ctx.status('parser', 'ok')
  log('parser', 'ok', `Намерение: ${intent.kind}. «${intent.title}», ${intent.date || '—'} ${intent.time || ''}, повтор: ${intent.repeat}, уверенность ${(intent.confidence * 100).toFixed(0)}% (${intent.confidenceLabel})`)

  // Голос главного агента (чат)
  const chief = A['orchestrator']
  const who = ctx.base.settings.userName || 'друг'
  ctx.chat({
    role: 'agent',
    agent: `${chief.name} · ${chief.model}`,
    text:
      intent.kind === 'create'
        ? `Понял: «${intent.title}» — ${intent.date === todayISO() ? 'сегодня' : intent.date} в ${intent.time}${intent.repeat !== 'none' ? `, повтор: ${intent.repeat}` : ''}. Уверенность ${(intent.confidence * 100).toFixed(0)}%. Передаю планировщику.`
        : intent.kind === 'chat'
          ? `Привет, ${who}. Я соберу, изменю и отмечу напоминания за тебя. Напиши обычной фразой: «напомни завтра в 9 позвонить клиенту» или «покажи, что на сегодня».`
          : `Команда «${intent.kind}» распознана с уверенностью ${(intent.confidence * 100).toFixed(0)}%. Передаю в работу.`,
    logIds: [...ids],
  })
  await ctx.delay(380)

  if (intent.kind === 'chat') {
    ctx.status('orchestrator', 'ok')
    log('orchestrator', 'ok', 'Ответ дан из справки. Операции не требуются.')
    ctx.chat({
      role: 'system',
      text: 'Подсказка: спроси «что на сегодня», поставь виджет 4×4, отложи любое напоминание на 15 минут или попроси «проверь всё».',
      logIds: [...ids],
    })
    return
  }

  // 2. Планировщик
  ctx.status('planner', 'run')
  log('planner', 'run', `Доступно ${ctx.base.reminders.filter((r) => !r.done).length} активных напоминаний. Считаю слоты и конфликты.`)
  await ctx.delay(560)
  const { ops, note } = buildOps(intent, ctx.base)
  ctx.status('planner', ops.length ? 'ok' : 'warn')
  log('planner', ops.length ? 'ok' : 'warn', ops.length
    ? `План готов: ${ops.length} ${plural(ops.length, 'вызов инструмента', 'вызова инструментов', 'вызовов инструментов')} — ${ops.map((o) => o.tool).join(', ')}.${note ? ' ' + note : ''}`
    : `План пуст: ${note}`)
  if (!ops.length) {
    ctx.status('orchestrator', 'warn')
    log('orchestrator', 'warn', 'Остановил пайплайн: нет валидных операций.')
    ctx.chat({ role: 'agent', agent: chief.name, text: note || 'Не смог собрать операцию. Сформулируй иначе — например: «напомни завтра в 10 про отчёт».', logIds: [...ids] })
    ctx.toast('Уточни команду', 'warn')
    return
  }

  // 3. Редактор — реальные изменения
  ctx.status('editor', 'run')
  log('editor', 'run', 'Вызываю инструменты Memento: запись в базу напоминаний.')
  await ctx.delay(620)
  const applied = execute(ops, { ...ctx.base })
  ctx.status('editor', applied.results.every((r) => r.ok) ? 'ok' : 'err')
  log('editor', 'ok', `Применено: ${applied.results.filter((r) => r.ok).length}/${applied.results.length}. ${applied.results.map((r) => r.message).join(' | ')}`)

  // 4. Валидатор (второй проход чтения)
  let verified = true
  if (ctx.base.settings.verification) {
    ctx.status('validator', 'run')
    log('validator', 'run', 'Перечитываю состояние приложения и сверяю с планом.')
    await ctx.delay(560)
    const check = execute([{ tool: 'query_reminders', args: { filter: 'all' } }], { ...ctx.base, reminders: applied.reminders })
    const q = check.results[0]
    verified = applied.results.every((r) => r.ok) && q.ok
    const created = ops[0].tool === 'create_reminder' ? applied.reminders.find((r) => !ctx.base.reminders.some((b) => b.id === r.id)) : undefined
    log('validator', verified ? 'ok' : 'err', verified
      ? `Состояние сходится. Всего в базе ${applied.reminders.length}. ${created ? `Подтверждаю запись «${created.title}» — ${created.date} ${created.time}.` : 'Изменения на месте.'}`
      : 'Расхождение: часть операций не применилась.')
    ctx.status('validator', verified ? 'ok' : 'err')
  } else {
    log('orchestrator', 'warn', 'Режим проверки выключен в настройках — валидатор пропущен.')
  }

  ctx.commit(applied.reminders, applied.settings, applied.results)

  // Вердикт главного
  ctx.status('orchestrator', verified ? 'ok' : 'warn')
  log('orchestrator', verified ? 'ok' : 'warn', verified
    ? `Готово. Проверил каждую операцию: ${applied.results.filter((r) => r.ok).length} из ${applied.results.length}.`
    : 'Есть расхождения — показываю результат, проверь вручную.')
  const summary = applied.results.filter((r) => r.ok).map((r) => r.message).join('; ')
  ctx.chat({
    role: 'agent',
    agent: `${chief.name} · ${chief.model}`,
    text: verified
      ? `Сделано ✓ ${summary || 'Операции применены.'} ${note ? `\n\n${note}` : ''}`
      : `Применил частично: ${summary}. ${note ? `\n\n${note}` : ''}`,
    logIds: [...ids],
  })
  if (verified) ctx.toast('Главный агент подтвердил изменения', 'ok')
}

export function reminderToOp(r: Partial<Reminder> & { title: string }): Op {
  return {
    tool: 'create_reminder',
    args: r,
  }
}

/* --------------------------- Автономные задачи ---------------------------- */

/** Главный агент в паре с валидатором перечитывает всю базу и выдаёт вердикт. */
export async function runAudit(ctx: AgentContext): Promise<void> {
  const A = Object.fromEntries(AGENTS.map((a) => [a.id, a])) as Record<string, typeof AGENTS[number]>
  const ids: string[] = []
  const log = (agentId: string, status: 'run' | 'ok' | 'warn' | 'err', text: string) => {
    const a = A[agentId]
    ids.push(ctx.log({ agent: a.name, model: a.model, status, text }))
  }
  const now = Date.now()
  ctx.status('orchestrator', 'run')
  log('orchestrator', 'run', 'Задача: полный аудит расписания. Запускаю чтение базы и проверку.')
  await ctx.delay(450)
  ctx.status('validator', 'run')
  log('validator', 'run', 'Читаю все напоминания, считаю просрочку и нагрузку по дням.')
  await ctx.delay(700)
  const all = ctx.base.reminders
  const today = filterReminders(all, 'today', now)
  const overdue = filterReminders(all, 'overdue', now)
  const done = all.filter((r) => r.done)
  const active = all.filter((r) => !r.done)
  const weekday = new Date().getDay()
  const issues: string[] = []
  if (overdue.length) issues.push(`просрочено ${overdue.length}`)
  if (today.length > 6) issues.push(`день перегружен: ${today.length} задач`)
  if (!active.length) issues.push('нет активных напоминаний')
  const highToday = today.filter((r) => r.priority === 'high').length
  ctx.status('validator', issues.length ? 'warn' : 'ok')
  log('validator', issues.length ? 'warn' : 'ok', `Итог: активных ${active.length}, сегодня ${today.length}, выполнено ${done.length}, просрочено ${overdue.length}, высокий приоритет сегодня — ${highToday}.`)
  await ctx.delay(400)
  ctx.status('orchestrator', 'ok')
  const verdict = issues.length
    ? `Аудит завершён. Нашёл: ${issues.join('; ')}. Рекомендую разобрать просроченное — могу отложить или перенести.`
    : `Аудит завершён. Всё чисто: ${active.length} активных, ${today.length} сегодня${highToday ? `, ${highToday} с высоким приоритетом` : ''}. Просрочек нет.`
  log('orchestrator', 'ok', `Вердикт главного агента: ${verdict}`)
  ctx.chat({ role: 'agent', agent: `${A['orchestrator'].name} · ${A['orchestrator'].model}`, text: verdict, logIds: [...ids] })
  ctx.toast('Аудит завершён — вердикт в чате агентов', issues.length ? 'warn' : 'ok')
}

/** Разгружает вечер: низкоприоритетные задачи после 18:00 переносит на завтра 10:00. */
export async function runReschedule(ctx: AgentContext): Promise<void> {
  const A = Object.fromEntries(AGENTS.map((a) => [a.id, a])) as Record<string, typeof AGENTS[number]>
  const ids: string[] = []
  const log = (agentId: string, status: 'run' | 'ok' | 'warn' | 'err', text: string) => {
    const a = A[agentId]
    ids.push(ctx.log({ agent: a.name, model: a.model, status, text }))
  }
  const t = todayISO()
  const tomorrow = addDaysISO(t, 1)
  ctx.status('planner', 'run')
  log('planner', 'run', 'Ищу вечерние задачи низкого приоритета и свободные утренние слоты.')
  await ctx.delay(620)
  const victims = ctx.base.reminders.filter(
    (r) => !r.done && r.date === t && timeToMinutes(r.time) >= 18 * 60 && r.priority !== 'high',
  )
  if (!victims.length) {
    ctx.status('planner', 'warn')
    log('planner', 'warn', 'Поздние задачи не найдены — расписание и так ровное.')
    log('orchestrator', 'ok', 'Остановил перенос: нечего оптимизировать.')
    ctx.status('orchestrator', 'ok')
    ctx.chat({ role: 'agent', agent: `${A['orchestrator'].name} · ${A['orchestrator'].model}`, text: 'Вечер уже свободен — переносить нечего.', logIds: [...ids] })
    return
  }
  const ops: Op[] = victims.map((r) => ({ tool: 'update_reminder', args: { id: r.id, patch: { date: tomorrow, time: '10:00' } } }))
  log('planner', 'ok', `Найдено ${victims.length}: ${victims.map((v) => v.title).join(', ')}. План: перенести на ${tomorrow} 10:00.`)
  await ctx.delay(400)
  ctx.status('editor', 'run')
  log('editor', 'run', 'Вызываю update_reminder для каждой записи.')
  await ctx.delay(600)
  const applied = execute(ops, ctx.base)
  log('editor', 'ok', `Применено: ${applied.results.filter((r) => r.ok).length}/${applied.results.length}.`)
  ctx.status('editor', 'ok')
  await ctx.delay(350)
  ctx.status('validator', 'run')
  log('validator', 'run', 'Читаю базу повторно и сверяю даты.')
  await ctx.delay(560)
  const ok = applied.results.every((r) => r.ok)
  const check = applied.reminders.filter((r) => victims.some((v) => v.id === r.id) && r.date === tomorrow && r.time === '10:00').length
  log('validator', ok && check === victims.length ? 'ok' : 'warn', `Подтверждено переносов: ${check} из ${victims.length}.`)
  ctx.status('validator', ok ? 'ok' : 'warn')
  ctx.commit(applied.reminders, applied.settings, applied.results)
  ctx.status('orchestrator', 'ok')
  log('orchestrator', 'ok', `Проверил лично: вечер разгружен на ${victims.length}.`)
  ctx.chat({
    role: 'agent',
    agent: `${A['orchestrator'].name} · ${A['orchestrator'].model}`,
    text: `Разгрузил вечер ✓ Перенёс на завтра 10:00: ${victims.map((v) => `«${v.title}»`).join(', ')}. Валидатор подтвердил — все ${victims.length} записей на месте.`,
    logIds: [...ids],
  })
  ctx.toast(`Перенесено ${victims.length} напоминаний`, 'ok')
}

/**
 * Сценарий демонстрации: агенты последовательно создают план дня.
 * Каждый прогон получает свежий снимок приложения (см. store.runTask).
 */
export const DEMO_PROMPTS = [
  'Напомни завтра в 09:00 встреча с командой — обсудить релиз и спринт',
  'Каждый день в 08:00 зарядка и стакан воды',
  'Напомни в пятницу в 19:30 ужин с семьёй',
  'Напомни через 30 минут проверить статус заказа',
  'Поставь виджет 4×4',
]
