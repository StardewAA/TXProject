# 防腐保温购物平台 PC Web

面向商城普通用户的桌面端 Web。项目与移动用户端共用 `/app-api`、商城租户 `1` 以及同一套会员、商品、购物车、地址、订单、支付和售后数据。

完整设计与开发边界见 [`../docs/商城PC端Web开发方案.md`](../docs/商城PC端Web开发方案.md)。

## 本地开发

要求 Node.js 22.18+、pnpm 11+，并确保网关可从 `http://localhost:48080` 访问。

```bash
pnpm install
pnpm dev
```

访问 `http://localhost:5175`。开发服务器会把 `/app-api` 代理到本地网关。

## 检查与构建

```bash
pnpm typecheck
pnpm build
```

## Docker 镜像

从当前目录构建：

```bash
docker build -t txgy-mall-web:local .
```

容器应加入现有 `txgy-dev` 网络，以便 Nginx 通过服务名 `gateway:48080` 访问网关：

```bash
docker run --name txgy-mall-web --network txgy-dev -p 5175:80 txgy-mall-web:local
```

当前尚未修改仓库级 `deploy/docker-compose.yml`。将容器纳入统一 Compose 前，需要按项目约定先确认部署文件冲突风险。

## 第三方支付页面预览

开发模式开启 `VITE_PAYMENT_PREVIEW=true`。容器镜像默认关闭，当前本地测试环境使用以下命令显式开启：

```bash
docker build --build-arg VITE_PAYMENT_PREVIEW=true --build-arg VITE_SMS_TEST_CODE=9999 -t txgy-mall-web:local .
```

- 收银台展示支付宝、微信支付入口，点击进入对应的扫码收银台，显示订单金额及“暂未接通”提示。
- 扫码区域为不可扫描的占位图，不生成假收款二维码；页面没有确认付款或模拟成功按钮，不调用支付提交接口，也不启动支付轮询。关闭后订单仍为待付款，正常超时关单规则不变。
- 两个入口不依赖后台配置真实渠道或 `mock` 渠道，也不会自动使用 `mock` 完成付款。原有独立的“模拟支付（测试）”和余额支付仍依据后台启用配置显示。
- 余额支付仍会扣减商城钱包余额；钱包充值不能使用余额支付。
- 接入真实支付后，配置商户渠道及公网回调，构建时设置 `VITE_PAYMENT_PREVIEW=false`，并在后台禁用测试用的 `mock` 渠道。现有真实渠道的二维码、支付表单和状态查询逻辑保留。环境开关在构建时生效，重启旧镜像不会改变开关。

## 数据同步规则

- 请求头固定发送 `tenant-id: 1` 和 Web 终端 `terminal: 20`。
- PC 与移动端分别登录，但相同账号对应同一后端会员。
- 购物车、地址、订单等以服务端为唯一数据源。
- 写操作成功后立即刷新相关数据，窗口重新获得焦点后进行节流刷新。
- 店铺名称以商品/店铺接口为准，不把缺失信息默认显示成“平台自营”。
