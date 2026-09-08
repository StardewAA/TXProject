FROM node:22-alpine AS builder
RUN apk add --no-cache git

WORKDIR /workspace/txgy-ui-admin-vben

RUN corepack enable && corepack prepare pnpm@11.7.0 --activate

COPY txgy-ui-admin-vben/ ./

RUN git init -q && pnpm install --frozen-lockfile

ENV NODE_OPTIONS=--max-old-space-size=2048
ENV VITE_BASE_URL=
ENV VITE_GLOB_API_URL=/admin-api
ENV VITE_APP_CAPTCHA_ENABLE=false
ENV VITE_APP_DOCALERT_ENABLE=false

RUN pnpm run build:antd

FROM nginx:alpine

# 主 nginx.conf (含 tenant-id map 条件默认值)
COPY deploy/frontend/nginx-main.conf /etc/nginx/nginx.conf
# default.conf (实际代理配置)
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/txgy-ui-admin-vben/apps/web-antd/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1