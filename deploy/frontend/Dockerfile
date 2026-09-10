FROM node:22-bookworm-slim AS builder

WORKDIR /workspace/txgy-ui-admin-vben

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
# CI 环境下 is-ci 短路 lefthook install（容器内无 git）
ENV CI=true

RUN corepack enable && corepack prepare pnpm@11.7.0 --activate

COPY txgy-ui-admin-vben/ ./

RUN git init -q && pnpm install --frozen-lockfile --fetch-retries=10 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000 --fetch-timeout=600000 --network-concurrency=4

ENV NODE_OPTIONS=--max-old-space-size=2048
ENV VITE_BASE_URL=
ENV VITE_GLOB_API_URL=/admin-api
ENV VITE_APP_CAPTCHA_ENABLE=false
ENV VITE_APP_DOCALERT_ENABLE=false

# 直接调用 turbo：绕过 package.json 中 cross-env 的 8GB 堆上限（VM 内存不足），
# 并限制并发构建数，避免 node 多进程内存峰值过大
RUN pnpm exec turbo build --filter=@vben/web-antd --concurrency=2

FROM nginx:1.27-alpine

COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/txgy-ui-admin-vben/apps/web-antd/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
