# syntax=docker/dockerfile:1
FROM node:22-alpine AS builder

WORKDIR /app
ARG VITE_SMS_TEST_CODE
ENV VITE_SMS_TEST_CODE=${VITE_SMS_TEST_CODE}
ARG VITE_PAYMENT_PREVIEW=false
ENV VITE_PAYMENT_PREVIEW=${VITE_PAYMENT_PREVIEW}
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG AMAP_BUILD_REV=local
RUN --mount=type=secret,id=amap_key \
    --mount=type=secret,id=amap_security_code \
    test -s /run/secrets/amap_key && \
    test -s /run/secrets/amap_security_code && \
    echo "map build revision: ${AMAP_BUILD_REV}" && \
    VITE_AMAP_KEY="$(cat /run/secrets/amap_key)" \
    VITE_AMAP_SECURITY_CODE="$(cat /run/secrets/amap_security_code)" \
    pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1/health || exit 1
