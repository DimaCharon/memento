import type { Priority, Reminder, Repeat } from '../types'

export const MONTHS_GEN = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
]
export const MONTHS_NOM = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]
export const MONTHS_SHORT = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']
export const WEEK_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
export const WEEK_FULL = [
  'воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота',
]

export const pad = (n: number) => String(n).padStart(2, '0')

export function toISO(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function fromISO(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, (m || 1) - 1, d || 1)
}

export function todayISO() {
  return toISO(new Date())
}

export function addDaysISO(iso: string, days: number) {
  const d = fromISO(iso)
  d.setDate(d.getDate() + days)
  return toISO(d)
}

export function timeToMinutes(t: string) {
  const [h, m] = t.split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}

export function minutesToTime(mins: number) {
  const m = ((mins % 1440) + 1440) % 1440
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`
}

export function reminderDate(r: Reminder) {
  return fromISO(r.date).getTime() + timeToMinutes(r.time) * 60000
}

export function fmtClock(d: Date) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtSeconds(d: Date) {
  return `${pad(d.getSeconds())}`
}

export function fmtLongDate(d: Date) {
  const wd = WEEK_FULL[d.getDay()]
  const wdCap = wd.charAt(0).toUpperCase() + wd.slice(1)
  return `${wdCap}, ${d.getDate()} ${MONTHS_GEN[d.getMonth()]}`
}

export function fmtShortDate(d: Date) {
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}. ${d.getFullYear()}`
}

export function dayLabel(iso: string) {
  const t = todayISO()
  if (iso === t) return 'Сегодня'
  if (iso === addDaysISO(t, 1)) return 'Завтра'
  if (iso === addDaysISO(t, -1)) return 'Вчера'
  const d = fromISO(iso)
  const sameYear = d.getFullYear() === new Date().getFullYear()
  return sameYear
    ? `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`
    : `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}`
}

export function fullDateLabel(iso: string) {
  const d = fromISO(iso)
  return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]} ${d.getFullYear()}`
}

/** «Через 24 мин» / «15 мин назад» / «Начало в 18:30» */
export function relativeLabel(target: number, now: number) {
  const diff = Math.round((target - now) / 60000)
  if (diff === 0) return 'Прямо сейчас'
  if (diff > 0) {
    if (diff < 60) return `Через ${diff} мин`
    if (diff < 60 * 24) {
      const h = Math.floor(diff / 60)
      const m = diff % 60
      return m ? `Через ${h} ч ${m} мин` : `Через ${h} ч`
    }
    const d = Math.round(diff / 1440)
    return `Через ${d} дн.`
  }
  const a = -diff
  if (a < 60) return `${a} мин назад`
  if (a < 60 * 24) return `${Math.floor(a / 60)} ч назад`
  return `${Math.round(a / 1440)} дн. назад`
}

export function greetingFor(d: Date) {
  const h = d.getHours()
  if (h < 5) return 'Доброй ночи'
  if (h < 12) return 'Доброе утро'
  if (h < 18) return 'Добрый день'
  return 'Добрый вечер'
}

export function greetingSub(d: Date, count: number) {
  if (count === 0) return 'На сегодня всё запланировано.'
  const h = d.getHours()
  if (h < 12) return 'Начнём день продуктивно.'
  if (h < 18) return `Сегодня важный день. Впереди ${count} ${plural(count, 'напоминание', 'напоминания', 'напоминаний')}.`
  return 'Сегодня важный день.\nВсё получится.'
}

export function plural(n: number, one: string, few: string, many: string) {
  const a = Math.abs(n) % 100
  const b = a % 10
  if (a > 10 && a < 20) return many
  if (b > 1 && b < 5) return few
  if (b === 1) return one
  return many
}

export function repeatLabel(r: Repeat) {
  switch (r) {
    case 'daily': return 'Ежедневно'
    case 'weekdays': return 'По будням'
    case 'weekly': return 'Каждую неделю'
    case 'monthly': return 'Каждый месяц'
    default: return 'Не повторять'
  }
}

export function priorityLabel(p: Priority) {
  return p === 'high' ? 'Высокий' : p === 'medium' ? 'Средний' : 'Низкий'
}

export function leadLabel(m: number) {
  if (m === 0) return 'В момент события'
  if (m < 60) return `За ${m} ${plural(m, 'минуту', 'минуты', 'минут')}`
  if (m < 1440) {
    const h = m / 60
    return `За ${h} ${h === 1 ? 'час' : h < 5 ? 'часа' : 'часов'}`
  }
  return `За ${m / 1440} ${m === 1440 ? 'день' : 'дня'}`
}

export function daysInMonth(year: number, month: number) {
  return new Date(year, month + 1, 0).getDate()
}

/** 0 = понедельник */
export function mondayIndex(year: number, month: number, day: number) {
  const wd = new Date(year, month, day).getDay()
  return (wd + 6) % 7
}

export function uid(prefix = 'id') {
  const rnd = Math.random().toString(36).slice(2, 8)
  return `${prefix}_${Date.now().toString(36)}${rnd}`
}

export function nextOccurrenceISO(dateISO: string, time: string, repeat: Repeat, now = Date.now()) {
  let iso = dateISO
  const at = () => fromISO(iso).getTime() + timeToMinutes(time) * 60000
  const step = () => {
    const d = fromISO(iso)
    switch (repeat) {
      case 'daily': d.setDate(d.getDate() + 1); break
      case 'weekdays': {
        d.setDate(d.getDate() + 1)
        while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + 1)
        break
      }
      case 'weekly': d.setDate(d.getDate() + 7); break
      case 'monthly': d.setMonth(d.getMonth() + 1); break
      default: return null
    }
    iso = toISO(d)
  }
  if (repeat === 'none') return null
  let guard = 0
  while (at() < now && guard < 500) {
    step()
    guard++
  }
  return iso
}

export function beep(sound: boolean) {
  if (!sound) return
  try {
    const Ctx = (window as any).AudioContext || (window as any).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const o = ctx.createOscillator()
    const g = ctx.createGain()
    o.type = 'sine'
    o.frequency.value = 880
    g.gain.value = 0.06
    o.connect(g)
    g.connect(ctx.destination)
    o.start()
    o.frequency.setValueAtTime(1180, ctx.currentTime + 0.12)
    o.stop(ctx.currentTime + 0.26)
    setTimeout(() => ctx.close?.(), 500)
  } catch {
    /* тишина, если браузер запретил звук */
  }
}
