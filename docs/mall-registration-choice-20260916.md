# 注册租户选择与集中部署记录（2026-09-16）

## 结论与边界

基线为本地 master `1a15a9d22`。本轮合并发布上一批“客户服务归属本人展示”和本批“注册租户选择准备”。代码尚未提交，未查看远程、未推送。仅涉及商城 member、PC、H5 与文档。

已完成注册选择界面、公共默认归属提示、最小注册目录、验证码随目标切换失效，以及客户归属展示的集中部署。**不等于完整跨租户注册/购物已上线**：跨租户实际发送验证码和注册仍在客户端前置拒绝，新认证/多租户开关保持关闭，现有入口仍为租户 1。

没有初始化公共租户或平台企业，没有把 1/3000 当作公共租户，没有迁移账号、复制商品、授予企业采购/销售权限。本轮无新 DDL，未执行项目数据库 SQL，未创建真实账号、验证码或登录会话，未使用会员 316/admin 的往次临时授权。

## 实现

### 后端

新增 `GET /app-api/member/mall-auth/registration-options`，匿名读取最小注册选项：

- 新认证或多租户关闭：enabled=false、publicTenantId=null、tenants=[]、crossTenantReady=false，不调用目录/企业校验 RPC。
- 开启时先通过现有策略校验公共租户及服务企业，再按明确配置的公开且可注册名单筛选；租户有效性重新核验。
- 公共租户在两次读取间失效/消失时拒绝返回，不静默回落其他租户。
- 目录仅含 tenantId/name，不暴露企业私有信息。
- crossTenantReady 固定 false。目录已准备不代表允许开放跨租户业务。
- 不改变现有认证发现与商城浏览能力声明。

### PC/H5

- 新协议注册页加载注册目录；PC 为可清空选择框，H5 为含“不选：平台自营公共商城”的选择器。
- 不选时解析为服务端明确返回的公共租户 ID，绝不硬编码回退 1/3000。
- 文案明确：仅客户服务归属，不自动成为企业员工，不自动获得采购/销售权限。
- 手机号、选择目标、认证入口或用途变化时同步清空验证码并使旧挑战失效；旧异步返回不得覆盖新选择。
- 目录 404 只兼容“没有租户选择”，不降级新认证协议；其他错误/非法目录阻止发码与提交，可重试。
- 当前 target != currentTenant 时，发送和注册前均拒绝。即使返回 crossTenantReady=true 也不越过当前安全边界。
- H5 曾经选中的租户被移出目录时明确要求重新选择，不伪装成已选择公共租户。
- 保持现有会员身份、协议、密码与注册后企业信息流程兼容。

## 验证结果

| 范围 | 结果 |
| --- | --- |
| member 选定回归 | 174 项通过 |
| product 现有选定回归 | 104 项通过，本轮无 product 源码修改 |
| PC 单元测试 | 215 项通过 |
| H5 单元测试 | 126 项通过 |
| 合计 | 619 项通过；相比上一批 581 项增加 38 项 |
| 后端 | member/product 所需 reactor clean package 成功 |
| PC | vue-tsc、Vite 构建成功 |
| H5 | build:h5 成功，现有 Sass 弃用告警保留 |
| 浏览器接口替身 | 26 组分别在本地构建产物和 Docker 前端产物上通过 |
| 实服只读部署检查 | 14 项通过 |
| 实服匿名注册页面 | PC/H5 两项通过，无脚本错误、无写请求 |
| 差异检查 | git diff --check 通过 |

26 组由 10 组认证/选择和 16 组客户归属场景组成；在两种部署入口重复执行，不算 52 个不同业务场景。API 替身场景不能替代真实双租户端到端验收。H5 选择器通过真实点击“当前企业商城”并完成替身注册，PC 覆盖 A→公共→A 后旧验证码不能提交。

最后一次并行浏览器运行中，本地 PC 旧协议兼容场景出现一次手机号输入框等待超时；Docker 同项通过。本地完整 10 组原脚本复跑全部通过，未修改业务代码，未将超时认定为已证明根因的业务缺陷。测试定位器早期对 H5 隐藏选择器副本与完成按钮的假设已修正。

实服仅验证关闭态、匿名访问拒绝、资源版本与页面就绪；没有新建真实会员、登录会员 316/admin、发真实短信或更改业务数据。

## Docker 集中更新

用户手动开启 Docker Desktop 后恢复引擎，启动原有 MySQL/Redis 容器；不重建数据库、不删卷、不改变端口/挂载。只更新三个应用镜像：

| 服务 | 镜像 | 入口 |
| --- | --- | --- |
| member | mall-member:registration-choice-20260916-1 | 网关 48080，内部健康 UP |
| PC | mall-pc:registration-choice-20260916-1 | http://localhost:15175 |
| H5 | mall-h5:registration-choice-20260916-1 | http://localhost:3000 |

后台 http://localhost:5173 保持可访问，未重建后台或其他无改动服务。member JAR、PC/H5 构建文件与运行容器哈希核对一致。旧镜像保留。

私有编排文件 `E:/txgy/local-backups/docker-organize-20260913/compose.current.json` 仅改变以上三项 image，其他配置与备份深比较一致。使用指定服务的 up --no-deps --no-build --pull never 更新，不整栈重建。备份含环境配置，禁止提交仓库或对外输出。

回退备份：`E:/txgy/local-backups/mall-registration-choice-20260916/compose.before.json`。如需回退，先核对当前运行状态与三个服务的镜像/配置差异，再按该备份记录的旧镜像定向重建这三个容器，不删除数据卷。本轮未执行回退。

## 证据与接续

证据目录：`E:/txgy/local-backups/mall-registration-choice-20260916/`。主要文件：
- backend-package.log、pc-tests.log、h5-tests.log、pc-build.log、h5-build.log。
- local-browser-result.json、docker-browser-result.json：最终 10 组认证/选择结果；browser-result.json 是早期中间文件，不作为最终结论。
- local-customer-browser-result.json、docker-customer-browser-result.json：各 16 组客户归属结果；后者读取 Docker 产物，脚本内部 mode 文本仍沿用 local static，不代表请求真实业务 API。
- smoke-result.json：14 项；anonymous-result.json：双端实服匿名页面。
- browser.cjs、customer-browser.cjs、smoke.cjs、anonymous.cjs 与截图。

接续顺序：
1. 完成登录/短信登录/找回密码目标上下文、首页浏览切换和链接/资源作用域。
2. 完成购物车、结算、订单及账号跨租户约束，再做独立双租户真实 E2E。
3. 上述闭环通过后，才允许评估移除 assertRegistrationContext 当前租户保护及开放 crossTenantReady/入口能力。
4. 向用户确认公共租户、平台自营企业、公开/可注册名单，再做初始化和受控启用。
5. 本轮两批改动已部署但未提交；询问用户是否提交本地 master。

使用 find-docs 核对 Vue 异步监听和 Compose 定向更新。Context7 配额不足后参考 [Vue 官方 watcher 文档](https://vuejs.org/guide/essentials/watchers.html) 与 [Docker Compose up 文档](https://docs.docker.com/reference/cli/docker/compose/up/)，落实同步挑战失效与仅更新指定服务的部署方式。

## 提交前复核（2026-09-16 下午）

用户授权“检查未提交代码，没问题就提交”。逐项复核本批与客户归属展示的 34 个文件，未发现阻断提交的问题；改动仅限商城 member、PC、H5 及专项文档，没有后台、ERP/WMS 或共享框架源码变更。未查看远程，不对远程冲突作保证。

重新执行 member 174、product 104、PC 215、H5 126 项，共 619 项通过，0 失败/错误/跳过；PC 类型检查通过。14 项 Docker 只读检查再次通过，运行产物匹配上轮构建，无需重复更新镜像。后端复核日志为证据目录内 precommit-backend-test.log。未执行项目数据库脚本，未建立真实账号会话；功能关闭和跨租户保护不变。本记录随代码提交本地 master，提交编号另记外部实施进度文档。
