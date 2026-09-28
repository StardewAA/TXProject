# txgy-cloud-portal

防腐保温行业门户前端工程初始化。

## 技术栈

- Vue 3 + Vite
- Vue Router
- Axios

## 开发

```bash
pnpm install
pnpm dev
```

默认访问 `http://localhost:5180`。

## 网关

Vite 已配置代理：

- `/admin-api` -> `http://localhost:48080`
- `/app-api` -> `http://localhost:48080`

请求封装位于 `src/api/request.js`，后续在 `src/views` 下继续接入 CMS 页面。
