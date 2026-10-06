# Запасной вариант: используется, если в настройках проекта выбран тип сборки «Docker».
# При типе «Node.js» платформа генерирует Dockerfile сама и этот файл не применяется.
# Multi-stage + NODE_ENV=production в финальной стадии (иначе образ остаётся в dev-режиме).

FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --include=dev
COPY . .
RUN npm run build

FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
COPY server.mjs ./
EXPOSE 8080
# Порт берём из окружения (платформа задаёт PORT=8080), слушаем 0.0.0.0
CMD ["node", "server.mjs"]
