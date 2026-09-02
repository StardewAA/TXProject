FROM node:22-bookworm-slim AS builder

ENV CI=true
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH

RUN corepack enable

WORKDIR /workspace

COPY txgy-ui-admin-vben/ ./

RUN --mount=type=cache,id=txgy-pnpm-store,target=/pnpm/store \
    pnpm install --frozen-lockfile

RUN pnpm build:antd

FROM nginx:1.27-alpine

COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/apps/web-antd/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
