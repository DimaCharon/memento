/** Юнит-тест парсера: фраза → ожидания. Запуск: node scripts/nlu-test.mjs */
import { parseIntent } from '/tmp/engine.mjs'

const world = { reminders: [], settings: { userName: 'Александр' }, now: Date.now() }
const pad = (n) => String(n).padStart(2, '0')
const iso = (offset) => { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }

const cases = [
  ['Напомни завтра в 9:00 встреча с командой', { title: 'Встреча с командой', date: iso(1), time: '09:00', repeat: 'none' }],
  ['Каждый день в 8:00 зарядка и стакан воды', { title: 'Зарядка и стакан воды', time: '08:00', repeat: 'daily' }],
  ['Напомни в пятницу в 19:30 ужин с семьёй', { title: 'Ужин с семьёй', time: '19:30', weekday: 5 }],
  ['Напомни через 30 минут проверить статус заказа', { title: 'Проверить статус заказа' }],
  ['напоминалка на завтра вечером купить продукты', { time: '19:00', date: iso(1) }],
  ['важно: позвонить клиенту завтра в 14:30', { priority: 'high', time: '14:30', date: iso(1) }],
  ['Отложи ужин с семьёй на 15 минут', { kind: 'snooze', minutes: 15, targetRef: 'ужин с семьёй' }],
  ['Отметь выполненным тест кнопки сохранить', { kind: 'complete', targetRef: 'тест кнопки сохранить' }],
  ['Удали напоминание про тренировку', { kind: 'delete', targetRef: 'про тренировку' }],
  ['Что у меня на сегодня?', { kind: 'query', query: 'today' }],
  ['Покажи все напоминания', { kind: 'query', query: 'all' }],
  ['Поставь виджет 4×4', { kind: 'widgets', layout: '4x4' }],
  ['Открой календарь', { kind: 'navigate', screen: 'calendar' }],
  ['Включи звук', { kind: 'settings', settingKey: 'soundEnabled' }],
  ['Выключи уведомления', { kind: 'settings', settingKey: 'notificationsEnabled', settingValue: false }],
  ['Создай напоминание на 12 сентября 2026 в 10:00 сдать отчёт', { date: '2026-09-12', time: '10:00', title: 'Сдать отчёт' }],
  ['Позвонить маме завтра в 18:00', { date: iso(1), time: '18:00', title: 'Позвонить маме' }],
  ['по будням в 7:30 зарядка', { repeat: 'weekdays', time: '07:30', title: 'Зарядка' }],
  ['Напомни про день рождения мамы 3 ноября в 12:00', { time: '12:00', title: 'Про день рождения мамы' }],
]

let pass = 0
for (const [phrase, exp] of cases) {
  const p = parseIntent(phrase, world)
  const problems = []
  for (const [k, v] of Object.entries(exp)) {
    if (k === 'weekday') {
      const wd = p.date ? new Date(p.date + 'T12:00:00').getDay() : -1
      if (wd !== v) problems.push(`weekday=${wd} ≠ ${v} (date=${p.date})`)
      continue
    }
    const got = p[k]
    if (k === 'targetRef') {
      if (!String(got || '').toLowerCase().includes(String(v).toLowerCase())) problems.push(`${k}="${got}" не содержит "${v}"`)
      continue
    }
    if (got !== v) problems.push(`${k}="${got}" ≠ "${v}"`)
  }
  if (problems.length) { console.log('❌', phrase, '\n   ', problems.join('; '), '\n    →', JSON.stringify({ kind: p.kind, title: p.title, date: p.date, time: p.time, repeat: p.repeat, priority: p.priority, conf: p.confidence.toFixed(2) })) }
  else { pass++; console.log('✅', phrase) }
}
console.log(`\nПройдено ${pass}/${cases.length}`)
process.exit(pass === cases.length ? 0 : 1)
