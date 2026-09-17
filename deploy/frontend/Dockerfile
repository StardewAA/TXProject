FROM node:22-bookworm-slim AS builder

WORKDIR /workspace/txgy-ui-admin-vben

# lefthook prepare 脚本需要 git
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN corepack enable && corepack prepare pnpm@11.7.0 --activate

COPY txgy-ui-admin-vben/ ./

# lefthook prepare 需要 git 仓库（.git 被 .dockerignore 排除，需初始化）
RUN git init -q

RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --no-frozen-lockfile

ENV NODE_OPTIONS=--max-old-space-size=2048
ENV VITE_BASE_URL=
ENV VITE_GLOB_API_URL=/admin-api
ENV VITE_APP_CAPTCHA_ENABLE=false
ENV VITE_APP_DOCALERT_ENABLE=false

RUN pnpm run build:antd

FROM nginx:1.27-alpine

COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/txgy-ui-admin-vben/apps/web-antd/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
