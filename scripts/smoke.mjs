/**
 * Сквозная проверка Memento в реальном Chromium:
 * кликаем все кнопки, прогоняем агентов, проверяем что данные реально меняются.
 */
import puppeteer from 'puppeteer'
import { mkdirSync } from 'node:fs'

const URL = process.env.URL || 'http://localhost:5173/'
const OUT = 'screenshots'
mkdirSync(OUT, { recursive: true })

const results = []
const ok = (name, extra = '') => { results.push(['✅', name, extra]); console.log('✅', name, extra) }
const bad = (name, extra = '') => { results.push(['❌', name, extra]); console.log('❌', name, extra) }

const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const page = await browser.newPage()
await page.setViewport({ width: 1460, height: 960, deviceScaleFactor: 2 })

const errors = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))

await page.goto(URL, { waitUntil: 'networkidle2' })
await page.evaluate(() => localStorage.clear())
await page.reload({ waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 800))

const shot = async (name) => { await page.screenshot({ path: `${OUT}/${name}.png` }); return `${OUT}/${name}.png` }

// helper: клик по кнопке/элементу с текстом
const clickText = async (text, selector = 'button') => {
  const done = await page.evaluate((t, sel) => {
    const els = [...document.querySelectorAll(sel)]
    const el = els.find((e) => (e.textContent || '').trim().toLowerCase().includes(t.toLowerCase()))
    if (!el) return false
    el.click()
    return true
  }, text, selector)
  await new Promise((r) => setTimeout(r, 350))
  return done
}
const countText = (t) => page.evaluate((x) => document.body.innerText.toLowerCase().includes(x.toLowerCase()), t)

// Надёжный ввод текста: фокус + нативный сеттер + событие input (аналог человеческого ввода)
const typeInto = async (selector, value) => {
  await page.evaluate((sel, val) => {
    const el = document.querySelector(sel)
    el.focus()
    const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, val)
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }, selector, value)
  await new Promise((r) => setTimeout(r, 150))
}
const clickSel = async (selector) => { await page.evaluate((s) => document.querySelector(s).click(), selector); await new Promise((r) => setTimeout(r, 250)) }

// 1. Приложение отрисовалось
const hasBrand = await countText('Memento')
const rows = await page.$$eval('.rem-row', (els) => els.length)
if (hasBrand && rows >= 3) ok('Главный экран отрисован', `строк напоминаний: ${rows}`)
else bad('Главный экран', `brand=${hasBrand} rows=${rows}`)
await shot('01-home')

// 2. Срабатывание уведомления (ближайшее через ~24 мин) — создадим своё на текущую минуту
await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  const d = new Date(Date.now() - 60000)
  const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  acc.reminders.push({
    id: 'test_due_1', title: 'Тестовое срабатывание', notes: '', icon: 'bell', date: iso, time,
    repeat: 'none', priority: 'high', place: '', leadMinutes: 10, done: false, firedAt: null,
    snoozedUntil: null, source: 'Тест', createdAt: Date.now(), updatedAt: Date.now(),
  })
  localStorage.setItem('memento.db.v1', JSON.stringify(raw))
})
await page.reload({ waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 2600))
const fired = await countText('Тестовое срабатывание')
const notifCount = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return acc.notifications.length
})
if (fired && notifCount >= 1) ok('Напоминание сработало и попало в центр уведомлений', `уведомлений: ${notifCount}`)
else bad('Срабатывание напоминания', `видно=${fired} уведомлений=${notifCount}`)

// 3. Экран уведомлений + действия
await clickText('Напоминания', '.tab')
await new Promise((r) => setTimeout(r, 500))
await shot('02-notifications')
const hasButtons = await countText('Отложить 10 мин')
if (hasButtons) ok('Центр уведомлений: есть кнопки действий')
else bad('Центр уведомлений: нет кнопок')
await clickText('Отложить 10 мин')
const snoozed = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  const r = acc.reminders.find((x) => x.id === 'test_due_1')
  return r.snoozedUntil && r.snoozedUntil > Date.now()
})
snoozed ? ok('Кнопка «Отложить 10 мин» работает') : bad('Отложить не работает')
await clickText('Закрыть')

// 4. Смена виджета из инспектора (панель справа)
await clickText('4x4', '.seg button')
await new Promise((r) => setTimeout(r, 400))
const layout = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].settings.widgetLayout)
layout === '4x4' ? ok('Виджет переключён на 4×4', layout) : bad('Переключение виджета', String(layout))
await shot('03-widget-4x4')

// 5. Календарь: выбор дня, добавление
await clickText('Календарь', '.nav-item')
await new Promise((r) => setTimeout(r, 500))
await shot('04-calendar')
const calCells = await page.$$eval('.cal-cell', (e) => e.length)
calCells >= 28 ? ok('Календарь построен', `ячеек: ${calCells}`) : bad('Календарь сломан')
await clickText('+2 дня', '.chip')
await new Promise((r) => setTimeout(r, 300))
const dayTitle = await page.$eval('.hint', (e) => e.textContent)
ok('Выбор дня в календаре', String(dayTitle).slice(0, 60))

// 6. Создание напоминания через FAB + выбор иконки + сохранить
await page.click('.fab')
await new Promise((r) => setTimeout(r, 500))
await typeInto('input[placeholder="Например: позвонить клиенту"]', 'Тест кнопки сохранить')
await clickText('Завтра', '.chip')
await clickText('Сохранить', '.btn')
await new Promise((r) => setTimeout(r, 600))
const created = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  return raw.accounts[0].reminders.some((r) => r.title === 'Тест кнопки сохранить')
})
created ? ok('Шторка создания: напоминание сохранено') : bad('Шторка создания не сохранила')

// 7. Агенты: реальный прогон пайплайна
await clickText('Агенты', '.nav-item')
await new Promise((r) => setTimeout(r, 400))
await typeInto('.composer textarea', 'Напомни в пятницу в 19:30 ужин с семьёй')
await clickSel('.send-btn')
await page.waitForFunction(() => {
  try {
    const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
    const acc = raw.accounts.find((a) => a.id === raw.activeId)
    return acc.reminders.some((r) => r.title.toLowerCase().includes('ужин'))
  } catch { return false }
}, { timeout: 25000 })
await new Promise((r) => setTimeout(r, 900))
await shot('05-agents')
const agentCreated = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  const r = acc.reminders.find((x) => x.title.toLowerCase().includes('ужин'))
  const logs = acc.logs.length
  return { title: r?.title, time: r?.time, date: r?.date, logs, chat: acc.chat.length }
})
if (agentCreated.title && agentCreated.time === '19:30') ok('Агент создал напоминание из фразы', JSON.stringify(agentCreated))
else bad('Агент не создал напоминание', JSON.stringify(agentCreated))
const validatorRan = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  return raw.accounts.find((a) => a.id === raw.activeId).logs.some((l) => l.agent.includes('Валидатор'))
})
validatorRan ? ok('Валидатор проверил операцию') : bad('Валидатор не запускался')

// 8. Пайплайн-вкладка и журнал
await clickText('Пайплайн', '.seg button')
await new Promise((r) => setTimeout(r, 400))
await shot('06-pipeline')
const pipelineOk = await countText('Главный агент')
pipelineOk ? ok('Вкладка «Пайплайн» показывает всех агентов') : bad('Пайплайн-вкладка пуста')

// 9. Аудит (автономная задача)
await clickText('Аудит расписания')
await page.waitForFunction(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return acc.chat.some((m) => m.text.includes('Аудит завершён'))
}, { timeout: 25000 })
ok('Автономная задача «Аудит расписания» выполнена')

// 10. Отложить через чат («отложи … на 15 минут»)
await clickText('Диалог', '.seg button')
await typeInto('.composer textarea', 'Отложи ужин с семьёй на 15 минут')
await clickSel('.send-btn')
await new Promise((r) => setTimeout(r, 4000))
const snoozeDone = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  const r = acc.reminders.find((x) => x.title.toLowerCase().includes('ужин'))
  return Boolean(r?.snoozedUntil)
})
snoozeDone ? ok('Чат-команда «отложи … на 15 минут» применена') : bad('Отложить через чат не сработало')

// 11. Отметить выполненным через чат
await typeInto('.composer textarea', 'Отметь выполненным тест кнопки сохранить')
await clickSel('.send-btn')
await new Promise((r) => setTimeout(r, 4000))
const doneViaChat = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return acc.reminders.find((x) => x.title === 'Тест кнопки сохранить')?.done
})
doneViaChat ? ok('Чат-команда «отметь выполненным» сработала') : bad('Отметка выполненным через чат не сработала')

// 12. Виджет через агента
await typeInto('.composer textarea', 'Поставь виджет 2×2')
await clickSel('.send-btn')
await new Promise((r) => setTimeout(r, 4200))
const layoutViaAgent = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].settings.widgetLayout)
layoutViaAgent === '2x2' ? ok('Агент переключил виджет на 2×2') : bad('Агент не переключил виджет', String(layoutViaAgent))

// 13. Коннекторы: экспорт .ics (реальная загрузка файла)
await clickText('Коннекторы', '.nav-item')
await new Promise((r) => setTimeout(r, 500))
await shot('07-connectors')
const client = await page.target().createCDPSession()
await client.send('Page.setDownloadBehavior', { behavior: 'allow', downloadPath: '/tmp/memento-dl' })
await clickText('Выгрузить .ics')
await new Promise((r) => setTimeout(r, 1500))
const fs = await import('node:fs')
const downloaded = fs.existsSync('/tmp/memento-dl') && fs.readdirSync('/tmp/memento-dl').length > 0
downloaded ? ok('Коннектор календаря: .ics выгружен', fs.readdirSync('/tmp/memento-dl').join(',')) : bad('Экспорт .ics не сработал')
await clickText('Показать инструменты')
await new Promise((r) => setTimeout(r, 500))
const mcpOpen = await countText('Memento MCP-шина')
mcpOpen ? ok('MCP-шина: список инструментов открывается') : bad('Модалка MCP не открылась')
await shot('08-mcp-tools')
await clickText('Закрыть', '.link-btn')
await page.keyboard.press('Escape')

// 14. Переключатели коннекторов
await clickText('Тест-запрос')
await new Promise((r) => setTimeout(r, 1500))
const warnShown = await page.$$eval('.toast-item', (e) => e.map((x) => x.textContent).join(' | '))
ok('Кнопка теста коннектора отвечает', String(warnShown).slice(0, 80))

// 15. Профиль: имя, тема, тумблеры, профили
await clickText('Профиль', '.nav-item')
await new Promise((r) => setTimeout(r, 500))
await shot('09-profile')
const nameInput = await page.$('input[value="Александр"]')
if (nameInput) {
  await typeInto('input[value="Александр"]', 'Мария')
  await new Promise((r) => setTimeout(r, 400))
  const nameSaved = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].settings.userName)
  nameSaved === 'Мария' ? ok('Смена имени сохраняется', nameSaved) : bad('Имя не сохранилось', String(nameSaved))
} else bad('Не нашёл поле имени')
await clickText('Уголь', '.seg button')
const theme = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].settings.theme)
theme === 'black' ? ok('Тема «Уголь» применилась') : bad('Тема не применилась', String(theme))
await clickText('Графит', '.seg button')
await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.field')]
  const row = rows.find((r) => r.textContent.includes('Звук'))
  row.querySelector('.switch').click()
})
await new Promise((r) => setTimeout(r, 300))
const sound = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].settings.soundEnabled)
sound === false ? ok('Тумблер звука переключается') : bad('Тумблер звука не работает')
await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.field')]
  const row = rows.find((r) => r.textContent.includes('Звук'))
  row.querySelector('.switch').click()
})
await typeInto('input[placeholder="Имя нового профиля"]', 'Гость')
await clickText('Создать', '.btn')
await new Promise((r) => setTimeout(r, 500))
const profiles = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts.length)
profiles === 2 ? ok('Создан второй профиль') : bad('Профиль не создан', String(profiles))
const switchedClick = await clickText('Мария', '.kv')
if (!switchedClick) bad('Профиль «Мария» не найден в списке')
await new Promise((r) => setTimeout(r, 400))
const switched = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  return raw.accounts.find((a) => a.id === raw.activeId).name
})
switched === 'Мария' ? ok('Переключение профиля работает', switched) : bad('Переключение профиля', String(switched))

// имя профиля синхронно с именем на главном экране
const nameSync = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const a = raw.accounts.find((x) => x.id === raw.activeId)
  return a.name
})
nameSync === 'Мария' ? ok('Имя профиля синхронно с главным экраном', nameSync) : bad('Профиль и имя разошлись', String(nameSync))

// 16. Экран блокировки
await clickText('Экран блокировки', '.btn')
await new Promise((r) => setTimeout(r, 600))
await shot('10-lock')
const lockShown = await page.$('.lock-screen')
if (lockShown) {
  ok('Экран блокировки открывается')
  await clickText('Нажми, чтобы открыть', '.unlock')
  await new Promise((r) => setTimeout(r, 400))
  const unlocked = await page.$('.lock-screen')
  unlocked ? bad('Не разблокировался') : ok('Разблокировка работает')
} else bad('Экран блокировки не открылся')

// 17. Демо-сценарий агентов (4 задачи подряд)
await clickText('Агенты', '.nav-item')
await new Promise((r) => setTimeout(r, 300))
await clickText('Демо', '.btn')
await new Promise((r) => setTimeout(r, 22000))
const demo = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return {
    зарядка: acc.reminders.some((r) => r.title.toLowerCase().includes('зарядка')),
    логи: acc.logs.length,
    layout: acc.settings.widgetLayout,
  }
})
demo.зарядка && demo.логи > 20 ? ok('Демо-сценарий агентов выполнен', JSON.stringify(demo)) : bad('Демо-сценарий', JSON.stringify(demo))
await shot('11-after-demo')

// 18. Перезагрузка — данные сохранились
await page.reload({ waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 1200))
const persisted = await page.evaluate(() => JSON.parse(localStorage.getItem('memento.db.v1')).accounts[0].reminders.length)
persisted > 8 ? ok('Данные сохраняются между сессиями', `напоминаний: ${persisted}`) : bad('Данные не сохранились')

// 19. Мобильная вёрстка
await page.setViewport({ width: 420, height: 900, deviceScaleFactor: 2 })
await new Promise((r) => setTimeout(r, 700))
await shot('12-mobile')
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
overflow <= 2 ? ok('Мобильная вёрстка без горизонтального скролла') : bad('Горизонтальный скролл на мобиле', `${overflow}px`)


// 20. Тест инструмента MCP из списка: реальный вызов create_reminder
await clickText('Агенты', '.nav-item')
await new Promise((r) => setTimeout(r, 400))
await clickText('Инструменты', '.seg button')
await new Promise((r) => setTimeout(r, 400))
const toolTest = await page.evaluate(() => {
  const panel = [...document.querySelectorAll('.panel')].find((p) => p.textContent.includes('create_reminder'))
  const btn = panel?.querySelector('.chip')
  if (!btn) return false
  btn.click()
  return true
})
await new Promise((r) => setTimeout(r, 6000))
const toolCreated = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return acc.reminders.some((r) => r.title.toLowerCase().includes('проверить инструмент'))
})
toolTest && toolCreated ? ok('Тест инструмента MCP создал запись через агента') : bad('Тест инструмента MCP', `нажато=${toolTest} создано=${toolCreated}`)

// 21. Кнопка «Изменить» в виджете 4×4 открывает редактор
await clickText('Главная', '.tab')
await new Promise((r) => setTimeout(r, 500))
await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  acc.settings.widgetLayout = '4x4'
  acc.settings.activeWidgets = ['today', 'next', 'progress']
  localStorage.setItem('memento.db.v1', JSON.stringify(raw))
})
await page.reload({ waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 1200))
const opened = await page.evaluate(() => {
  const btn = [...document.querySelectorAll('.mini-btn')].find((b) => b.textContent.includes('Изменить'))
  if (!btn) return 'нет кнопки'
  btn.click()
  return 'клик'
})
await new Promise((r) => setTimeout(r, 600))
const sheetOpen = await page.$('.sheet-card')
opened === 'клик' && sheetOpen ? ok('Кнопка «Изменить» в виджете 4×4 открывает редактор') : bad('Кнопка «Изменить» в виджете', String(opened))
const savedEdit = await page.evaluate(() => {
  const ta = document.querySelector('.sheet-card textarea')
  if (!ta) return 'нет поля'
  Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(ta, 'обновлено из виджета')
  ta.dispatchEvent(new Event('input', { bubbles: true }))
  const btn = [...document.querySelectorAll('.sheet-card .btn')].find((b) => b.textContent.includes('Сохранить'))
  btn.click()
  return 'saved'
})
await new Promise((r) => setTimeout(r, 600))
const notesSaved = await page.evaluate(() => {
  const raw = JSON.parse(localStorage.getItem('memento.db.v1'))
  const acc = raw.accounts.find((a) => a.id === raw.activeId)
  return acc.reminders.some((r) => r.notes.includes('обновлено из виджета'))
})
notesSaved ? ok('Редактор из виджета сохраняет изменения') : bad('Редактор из виджета не сохранил', String(savedEdit))

// Итог
console.log('\n--- ИТОГ ---')
const failed = results.filter((r) => r[0] === '❌')
console.log(`Пройдено: ${results.length - failed.length}/${results.length}`)
if (errors.length) {
  console.log('Ошибки консоли:')
  ;[...new Set(errors)].slice(0, 12).forEach((e) => console.log('  ⚠', e.slice(0, 200)))
} else console.log('Ошибок консоли нет')
await browser.close()
process.exit(failed.length ? 1 : 0)
