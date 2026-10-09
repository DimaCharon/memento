/** Скриншоты экранов для визуальной проверки */
import puppeteer from 'puppeteer'
import { mkdirSync } from 'node:fs'
const browser = await puppeteer.launch({ args: ['--no-sandbox','--disable-dev-shm-usage'] })
const page = await browser.newPage()
await page.setViewport({ width: 1460, height: 980, deviceScaleFactor: 2 })
mkdirSync('screenshots', { recursive: true })
await page.goto('http://localhost:5173/app.html', { waitUntil: 'networkidle2' })
await page.evaluate(() => localStorage.clear())
await page.reload({ waitUntil: 'networkidle2' })
await new Promise(r=>setTimeout(r,700))
const box = await page.$('.phone-frame').then((el) => el.boundingBox())
const clip = { x: box.x - 8, y: box.y - 8, width: box.width + 16, height: box.height + 16 }
const go = async (label, sel) => {
  await page.evaluate((t) => {
    const el = [...document.querySelectorAll('.nav-item, .tab, .seg button')].find(e=>e.textContent.trim().startsWith(t))
    if (el) el.click()
  }, sel)
  await new Promise(r=>setTimeout(r,700))
  await page.screenshot({ path: `screenshots/${label}.png`, clip })
  console.log('shot', label)
}
await go('s-home', 'Главная')
await go('s-widgets', 'Виджеты')
await go('s-calendar', 'Календарь')
await go('s-agents', 'Агенты')
// спросим агента и подождём
await page.evaluate(() => {
  const ta = document.querySelector('.composer textarea')
  Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set.call(ta, 'Напомни в пятницу в 19:30 ужин с семьёй')
  ta.dispatchEvent(new Event('input', { bubbles: true }))
})
await page.evaluate(() => document.querySelector('.send-btn').click())
await new Promise(r=>setTimeout(r,6000))
await page.screenshot({ path: 'screenshots/s-agents-chat.png', clip })
// пайплайн
await page.evaluate(() => [...document.querySelectorAll('.seg button')].find(e=>e.textContent.includes('Пайплайн')).click())
await new Promise(r=>setTimeout(r,600))
await page.screenshot({ path: 'screenshots/s-agents-pipeline.png', clip })
await go('s-connectors', 'Коннекторы')
await go('s-profile', 'Профиль')
await go('s-notifications', 'Напоминания')
// шторка создания
await go('s-home2', 'Главная')
await page.evaluate(() => document.querySelector('.fab').click())
await new Promise(r=>setTimeout(r,700))
await page.screenshot({ path: 'screenshots/s-new-reminder.png', clip })
await page.keyboard.press('Escape')
// экран блокировки (кнопка в сайдбаре)
await page.evaluate(() => [...document.querySelectorAll('.side .btn')].find(e=>e.textContent.includes('Экран блокировки')).click())
await new Promise(r=>setTimeout(r,900))
await page.screenshot({ path: 'screenshots/s-lock.png', clip })
await browser.close()
