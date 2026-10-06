# Memento — заметки для ИИ-агентов

## Деплой на RelaxDev

Инструкция платформы лежит в репозитории: `.claude/skills/relaxdev-deploy/SKILL.md`
(официальная версия: https://relaxdev.ru/.well-known/skills/relaxdev-deploy/SKILL.md).

Что уже учтено в проекте под требования платформы:

- **Порт 8080.** `server.mjs` читает `process.env['PORT']` (fallback `SERVER_PORT`, затем 8080)
  и слушает `0.0.0.0` — домен не отдаст 502.
- **Переменные и заглушки.** Все окружение читается через квадратные скобки
  (`process.env['ANTHROPIC_API_KEY']`), поэтому подмена `auto-generated-stub-for-build` не ломает
  фолбэки. Дополнительно значение-заглушка распознаётся и игнорируется (`real()`).
- **Один постоянный процесс.** `npm run build` собирает фронтенд, `npm start` запускает
  Node-сервер на переднем плане: статика из `dist/` + шлюз `/api/claude` и `/api/outbound`.
- **Прокси платформы.** Если задан `OUTBOUND_PROXY_URL` или `RELAXDEV_PROXY_URL`, исходящие
  запросы идут через `undici.ProxyAgent` — встроенный `fetch` в Node переменные прокси не читает.
- **Telegram.** Для веб-проекта нужна переменная `TG_OPTION=1` (IPv6) либо прокси.
- **Dockerfile** в репозитории есть, но применяется только при типе сборки «Docker».

Скрипты проверки: `npm run test:nlu` (19 кейсов разбора фраз), `npm run test:smoke`
(32 сквозных проверки в Chromium; требует `npm i -D puppeteer` и запущенный `npm run dev`).
