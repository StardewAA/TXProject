# GoView 大屏设计器前端镜像：Node 构建产物 + Nginx 托管。
#
# 构建上下文 = 仓库根目录（compose 里 context: ..），因此 COPY 必须以 txgy-ui-go-view/ 开头；
# 同名 Dockerfile.dockerignore 已把上下文裁剪到只含本项目与 deploy/goview/，避免把
# .docker-local（数 GB）等无关目录传给 Docker daemon。
#
# 后端接口说明：GoView 统一请求 /admin-api/**（见 src/settings/httpSetting.ts 的 axiosPre），
# 由本镜像内的 Nginx 反向代理到同一 txgy-dev 网络里的 gateway:48080。

FROM node:22-bookworm-slim AS builder

WORKDIR /workspace/txgy-ui-go-view

# husky 的 postinstall 需要 git；国内网络用 npmmirror 拉依赖
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && corepack enable \
    && corepack prepare pnpm@11.7.0 --activate \
    && pnpm config set registry https://registry.npmmirror.com

COPY txgy-ui-go-view/ ./

# husky install 要求存在 git 仓库（仓库根 .git 已被 dockerignore 排除）
RUN git init -q

RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --no-frozen-lockfile

# 构建期环境变量：
#   VITE_PRO_PATH  为空串 —— 生产 base_url 拼成 '' + '/admin-api'（留空是必需的，
#                  未定义时 Vite 会注入 undefined，base_url 变成 "undefined/admin-api"）
#   VITE_PROD_PATH 供 appendTokenAndTenant 判断"是否自家接口"，需与 axiosPre 一致
#   VITE_APP_TENANT_ENABLE=true —— 登录页会用租户名换取 tenant-id 头（当前库中 OPENLAB BS）
ENV NODE_OPTIONS=--max-old-space-size=4096 \
    VITE_DEV_PORT=3000 \
    VITE_DEV_PATH=/admin-api \
    VITE_PRO_PATH= \
    VITE_PROD_PATH=/admin-api \
    VITE_ROUTER_DEFAULT=false \
    VITE_APP_CAPTCHA_ENABLE=false \
    VITE_APP_TENANT_ENABLE=true

# 用 build:prod（vite build）而不是 build（vue-tsc 全量类型检查），
# 避免历史遗留的类型错误阻断镜像构建。
RUN pnpm run build:prod

FROM nginx:1.27-alpine

COPY deploy/goview/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/txgy-ui-go-view/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
