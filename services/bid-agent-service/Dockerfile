FROM node:22-alpine

WORKDIR /app

# 只装运行时依赖（@modelcontextprotocol/sdk）；dist 已在宿主机编译好
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY dist ./dist
COPY .env ./.env
COPY tools ./tools
COPY templates ./templates

EXPOSE 8199

CMD ["node", "dist/index.js"]
