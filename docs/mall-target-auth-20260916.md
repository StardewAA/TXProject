# 目标租户认证与会话核验：本地开发记录（2026-09-16）

## 交付边界

基线为本地 master `f99b5e3d7`，在上一批“跨商城匿名浏览”的未提交改动上继续开发，没有覆盖或提交上一批工作。用户要求继续补齐、后续集中更新 Docker，本轮仅修改商城 member、PC、H5 和文档。

**本批完成的是目标认证传输与可信会话核验，不是完整多租户购物上线。** 两端现有新认证表单已接入新的确认逻辑；默认入口仍为租户 1。注册选择保护、主业务浏览上下文保护未移除，tenantSelection/browseSwitch/registrationSelection 仍未开放。不能把本地目标租户协议测试当成目标租户页面注册、完善企业资料及下单全部可用。

未查看远程仓库、未提交/推送，未修改数据库、未发送真实短信、未使用会员 316/admin、未初始化公共租户或企业、未改运行配置、未更新/重启 Docker。

## 已完成

### 1. 服务端按目标发现认证协议

`GET /app-api/member/mall-auth/config?tenantId=<目标>` 支持显式目标：

- 不传目标保持原兼容入口，不把认证配置接口的缺省值误当成公共注册默认值；公共注册解析仍使用既有 policy.resolve(null, true)。
- 新认证关闭时，原入口仍返回 legacy；其他目标返回不可用的 challenge-v1，不能误导客户端调用固定租户的旧注册。
- 新认证开启时，分别核对目标登录和注册策略，复用公开名单、状态/到期、请求头一致性和公共客户归属校验。
- 无效编号、目标不在目录、注册关闭或目标解析不一致均不开放；不会退到租户 1。
- 返回协议能力不代表完整业务切换已准备好；tenantSelection 继续 false。

### 2. PC/H5 使用相同认证核心

新增两端字节一致的 `tenant-auth-client.mjs`，PC API 适配及 H5 可见认证表单已真实引用，不是只有测试代码。

- 认证请求明确绑定目标，只允许五种新认证操作；丢弃调用方附加的 role、enterpriseId、URL、headers 等字段。
- 验证手机号、协议、目标编号、验证码用途/挑战、注册身份和协议确认；未知操作不能拼接任意接口。
- 协议发现不带当前账号令牌/租户头；发码、密码登录、验证码登录、注册和忘密不携带当前账号令牌。
- 收到登录/注册令牌后，先校验目标、会员及令牌格式，再仅用该候选令牌调用既有 /member/mall-tenant/session。
- 服务端确认租户和会员都一致，才返回给现有 store 保存；会话接口 404、500、错租户或错会员均不降级为旧认证。
- 切换表单、关闭弹窗、退出或其他登录使旧操作失效；旧结果不能存入新账号会话。
- 原入口协议发现 404 的旧后端兼容保留；目标租户发现 404 绝不退回租户 1。
- 注册接口可能已经提交，但登录核验失败时提示“注册请求已返回，但登录状态未确认；请勿重复注册，稍后使用该商城账号登录”，不自动重试创建账号。

认证核心支持任意合法显式目标，但主页面当前的固定入口保护仍存在。配置查询/传输支持目标，不等于已允许用户在主商城切换并购物。

### 3. 刷新与购物请求保护

PC/H5 对已有可信会员上下文的令牌刷新增加同租户、同会员和新令牌会话核验；不通过时不保存新令牌，也不重放等待中的购物车/订单请求。

补测同账号并发 401 共用刷新、错误/缺失会员、错误租户、刷新核验期间新登录、旧购物写入等待后不能以新账号执行。原账号未确认时的旧入口兼容仍保留；完整的启动会话恢复、全资源多租户路由和业务隔离不是本批已完成项。

## 验证结果

| 项目 | 通过数/结果 |
| --- | --- |
| member 选定回归 | 184 |
| product 选定既有回归 | 104，未修改 product 源码 |
| PC 单元测试 | 279 |
| H5 单元测试 | 184 |
| 合计 | 751，比上一批 671 增加 80 |
| PC 构建 | vue-tsc 与生产构建成功 |
| H5 构建 | 成功，既有 Sass 弃用告警仍存在 |
| 新会话失败/晚响应页面场景 | PC/H5 各 4，共 8 |
| 原认证/注册页面回归 | 10 |
| 上一批跨商城匿名浏览回归 | 8 |
| 原客户归属页面回归 | 16 |
| 本地浏览器合计 | 42 组 |

浏览器使用本地生产构建，所有 API 均拦截为隔离响应替身，无真实业务请求。测试涵盖正向注册及企业资料跳转、错误候选身份不写令牌、会话端点失败不降级、关闭表单后晚响应、原账号保留及公开预览安全边界。

过程记录：第一次 Maven 命令使用了不存在的 product-biz 模块名，未进入编译；改为仓库实际 product-server 后选定 reactor 测试成功。H5 首次回归有 1 个旧断言把最后请求当成注册请求；新增会话 GET 后断言失效，改为按注册路径定位，同时补充真实会话失败与晚响应测试，最终全部通过。没有为了通过测试移除安全保护。

证据：`E:/txgy/local-backups/mall-target-auth-20260916/`，含单测/构建/后端日志、新 browser.cjs 与报告、复用回归报告及截图。临时本地静态服务为 5185/5184，结束后关闭；Docker 15175/3000 页面仍是既有镜像版本。

## 本地复跑

PC：在 txgy-mall-web 执行 `pnpm test:unit`、`pnpm build`。
H5：在 txgy-mall-uniapp-master 执行 `node --test --test-reporter=spec tests/*.test.mjs`、`pnpm build:h5`。

仓库根目录后端命令：

```powershell
mvn -o -B -ntp -pl txgy-module-member/txgy-module-member-server,txgy-module-mall/txgy-module-product-server -am '-Dtest=Mall*Test,AppMall*Test,MemberAuthTenantTest,MemberPointRecordIdempotencyTest,InquiryWorkbookGuardTest,EnterpriseMembershipServiceTest,InquiryExcelTest,PurchaseInquiryServiceTest,ProductSpuReviewTest,SupplierProductServiceTest,SupplierShopServiceTest,SupplierStockServiceTest,WmsReadonlyBoundaryTest,ProductReview*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 接下来仍需补齐

1. 把主页面浏览租户、认证目标、可信账号租户分离并接通 UI；目标注册完成后，在正确租户进入企业资料页面。
2. 购物车、结算、订单/售后、地址、询价、上传/下载、深链接、多标签页和页面缓存的完整范围适配；不能把本批刷新保护当成全资源验收。
3. 两个真实隔离租户的端到端数据回归、启动恢复及整套业务页面验证；公共租户、自营企业和公开/注册名单仍待用户确认。
4. 按用户要求累积完整批次后统一更新 Docker；实际发布时重新验收服务端会话核验/刷新与网关行为。

find-docs 本轮检索仍遭遇 Context7 配额限制，已告知用户并查阅 [Vue Router 官方导航守卫文档](https://router.vuejs.org/guide/advanced/navigation-guards.html) 核对现有路由保护边界。本批未移除主路由保护；是否可用以项目实际构建与测试为准。

## 当日后续：集中部署与提交前复核（2026-09-16）

用户授权更新 Docker、检查未提交代码，通过后提交。上文“未部署/未提交”是开发完成时的历史状态，本节为部署后状态。

- 复核范围包含本文件对应认证补齐及上一批独立跨商城匿名浏览；变更仅限商城 member、PC、H5 和文档。未发现阻断提交的问题；不把尚未开放的全租户业务标为完成。
- 重新通过 member 184、product 104、PC 279、H5 184，共 751 项自动测试；后端选定 reactor clean package、PC 类型检查和双端生产构建成功。
- 仅重建三个镜像：mall-member、mall-pc、mall-h5，统一标签 `market-auth-20260916-1`；旧镜像保留。其余商城依赖仅启动现有容器，无数据卷删除或数据库重建，没有操作其他项目容器。
- 私有编排文件仅三个 image 值变化，端口、挂载、环境等经深比较保持原样；依照 [Docker Compose 官方定向更新说明](https://docs.docker.com/reference/cli/docker/compose/up/) 使用指定服务的 --no-deps --no-build --pull never，未整栈重建。
- 17 项实服只读部署检查通过，核对容器镜像 ID、JAR/前端资源哈希、会员健康、目标认证关闭态、匿名会话拒绝和原租户 1 入口；PC/H5 实服匿名注册页均可用，无写请求或脚本异常。
- Docker 页面产物上的 42 组 API 替身场景通过（新会话 8、匿名跨商城浏览 8、注册 10、客户归属 16）。这些不是真实跨租户交易或真实会员登录验收；没有复用历史 316/admin 临时验证码权限。
- 公共租户/名单未初始化，browsePreview、tenantSelection 等未启用，原入口仍为租户 1。没有执行业务 SQL、发短信、创建账号或更改账号权限；未查看/推送远程。

入口：PC http://localhost:15175/，H5 http://localhost:3000/，后台 http://localhost:5173/，网关 48080。
证据：`E:/txgy/local-backups/mall-market-auth-deploy-20260916/`。
回退配置备份：该目录的 compose.before.json（含私有配置，不入 Git）。如需回退，核对当前状态后只恢复三个旧镜像引用并定向重建，不删除数据卷。本轮没有执行回退。

代码随本记录提交本地 master，具体提交编号记录在外部实施进度中。
