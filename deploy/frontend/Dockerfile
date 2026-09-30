FROM node:22-bookworm-slim AS builder

WORKDIR /workspace/txgy-ui-admin-vben

# lefthook prepare 脚本需要 git
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN corepack enable && corepack prepare pnpm@11.7.0 --activate

COPY txgy-ui-admin-vben/ ./

# lefthook prepare 需要 git 仓库（.git 被 .dockerignore 排除，需初始化）
RUN git init -q

# Dependency resolution also consumes a Node heap; bound it before installation,
# not only before Vite. This is not a total container memory limit.
ARG INSTALL_NODE_HEAP_MB=2048
ENV NODE_OPTIONS=--max-old-space-size=${INSTALL_NODE_HEAP_MB}
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile

ARG BUILD_NODE_HEAP_MB=4096
ENV NODE_OPTIONS=--max-old-space-size=${BUILD_NODE_HEAP_MB}
ENV VITE_BASE_URL=
ENV VITE_GLOB_API_URL=/admin-api
ENV VITE_APP_CAPTCHA_ENABLE=false
ENV VITE_APP_DOCALERT_ENABLE=false
ARG VITE_MALL_PC_URL=
ENV VITE_MALL_PC_URL=${VITE_MALL_PC_URL}

# Root build:antd calls build, which hard-codes an 8 GB Node heap. Invoke turbo
# directly so the explicit container build argument remains effective.
RUN pnpm exec turbo run build --filter=@vben/web-antd --concurrency=1

FROM nginx:1.27-alpine

COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/txgy-ui-admin-vben/apps/web-antd/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
