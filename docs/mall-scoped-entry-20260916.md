# 主商城入口切换与移动企业资料：本地开发记录（2026-09-16）

> 后续状态：本批已于当日按用户指示完成集中 Docker 更新和提交前复核；下文“未部署/未提交”为开发阶段原始记录。最新部署、验证与保留项见 [集中部署记录](mall-scoped-entry-deploy-20260916.md)。

## 本轮范围与状态

用户要求“继续下一步开发，后续一起更新 Docker”。基于本地 master 的 e181ce84a，保留上一轮未提交的启动会话恢复改动继续开发。本轮未提交、未推送、未查看远程；未修改数据库、账号密码或权限，未使用真实验证码会话，未启动/更新/清理 Docker 服务。

这是一批**默认关闭、待完整范围验收的多租户入口代码**，不是多租户全链路上线。公共租户编号、公开/注册名单仍待用户确认，不能自行使用 3000、9001 等编号初始化真实租户。测试中的 9001/9002 均为隔离响应数据。

## 已实现

1. PC/H5 增加主商城选择入口，点击前重新校验公开目录，切换后整页重载；不在旧页面上替换账号租户。
2. 新增 mall.tenant.scoped-entry-enabled（默认 false），仅在目录有效且显式启用时返回 capabilities.scopedEntry=true。既有 browseSwitch、registrationSelection、crossTenantReady 不因本批自动变为 true。
3. 原商城令牌键不变；其他商城令牌分别存入 :tenant:<id> 命名空间。注销/身份失配只清理当前入口令牌；不搬迁或改绑账号。
4. sessionStorage 的 mall-scoped-entry 和 URL 中 tenantId/tenant-id/tenant_id 都只是提示，不是权限。非法/冲突参数、停用目录、不可用目录均禁止目标商城业务请求，不退回原商城读取同编号数据。
5. PC 普通请求、H5 普通请求/上传继续使用核验后的入口和账号身份。非原商城入口重新检查目录；过期会话及刷新仍需验证租户与会员一致。目录失效保留本地令牌以便重试，不擅自注销原商城。
6. 注册选其他租户时，当前页面不能直接发送验证码或注册；先点击“进入所选商城注册”，在新入口重新填写并获取绑定该租户的验证码。未选择租户仍按独立 registration-options 的公共商城配置处理。
7. PC 注册成功继续进入企业资料页；H5 新增 /pages/user/enterprise-profile，并在注册成功后跳转，个人中心提供再次进入入口。
8. H5 企业资料复用已有 /product/enterprise-membership/mine、/apply 接口，支持 PNG/JPEG 营业执照（1MB 内）、提交、待审只读、审核状态刷新、驳回后修改重提。申请载荷仅白名单字段，不提交 tenantId/memberId/status/enterpriseId 等权限字段。
9. 企业资料读写和图片读取防止旧账号/旧页面异步结果覆盖；客户端不自行授予企业采购、销售或店铺权限。
10. H5 不再恢复旧 app-store 装修/分享缓存；搜索历史与推广来源按租户分开。PC 结算草稿按租户＋会员分开，旧无归属草稿不自动迁移，需要重新选择商品。账号变化时清理结算页旧资料。原商城预置商品图片仅在原商城使用，不覆盖新商城同编号商品。

## 安全边界及尚未完成项

- 禁止对非原商城回退到旧版登录、验证码或第三方认证接口；目标入口走已有租户绑定认证。
- H5 旧实时客服 WebSocket 在非原商城不建立连接，不能把此保护标记为多租户客服已完成；原商城行为保留。
- 本轮 H5 企业上传实现范围为浏览器 H5；原生端/小程序给出转 H5/PC 办理提示，未宣称这些平台已验收。
- 企业审核及供应商店铺关联仍由现有后端/后台流程控制；未自动给普通客户开通企业角色。
- 后续需继续验收：全业务页与所有新窗口/复制链接的租户范围、客服消息和附件、第三方支付/地图/物流、企业经营资源、下单到售后全链路及旧账号完整回归。本批地址栏租户参数与 H5 分享租户参数不等于所有外跳入口已完成验收。
- 启用之前必须确认公开与注册名单、目标租户装修/商品/店铺/物流模板/经营账号等资源，并完成真实双租户数据隔离与回退测试。不要仅因 scopedEntry 代码已存在就打开生产开关。

## 本地验证

- PC：327 项自动测试通过；vue-tsc 类型检查及生产构建通过。
- H5：228 项自动测试通过；H5 生产构建通过。
- member：185 项定向测试通过（含目录开关、认证、短信挑战、会话、积分幂等等），Maven 离线 reactor 成功。本轮未改 product 后端，未把其历史测试数量计入。
- 合计 740 项自动测试。H5 构建仍有现有 Sass 弃用警告，无构建错误。
- 浏览器验收使用 Edge＋本地构建静态服务，全部 API 拦截为隔离数据；不代理真实后端、不发真实短信、不写业务数据库。这不是真实多租户购物 E2E。
- 新增 13 组：两端非法注册参数、匿名目标入口、关闭目标入口、已核验目标企业页面；PC/H5 菜单切换；PC 目标注册跳转；H5 目标注册跳转；H5 图片上传/提交/待审/驳回重提。
- 另回归原 54 组：启动恢复 12、认证身份 8、注册 10、匿名商城浏览 8、客户归属展示 16。最终结果以同目录各 JSON 报告为准。

证据目录：E:/txgy/local-backups/mall-session-restore-20260916/
- scoped-entry-browser.cjs / scoped-entry-browser.json
- pc-scoped-entry-build.log / h5-scoped-entry-build.log / h5-scoped-entry-tests.log
- pc-foreign-enterprise.png / h5-foreign-enterprise.png / h5-enterprise-resubmit.png
- 原 browser.cjs、auth-browser.cjs、registration-browser.cjs、market-browser.cjs、customer-browser.cjs 及报告。

复跑：
- PC：npm run test:unit；npm run build
- H5：node --test tests/*.test.mjs；npm run build:h5
- 后端根目录：mvn -o -B -ntp -pl txgy-module-member/txgy-module-member-server -am '-Dtest=Mall*Test,AppMall*Test,MemberAuthTenantTest,MemberPointRecordIdempotencyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
- 浏览器：启动证据目录的 preview.cjs（5185/5184，仅静态内容，不代理 API），再运行上述浏览器脚本。不可将测试服务暴露到生产。

## 文件边界与交接

代码修改仅在商城 PC、H5、member 的 mall tenant 目录/配置/测试及文档；没有修改后台管理、ERP、WMS 或共享框架源代码。本地分支仍为 master；未检查远程，因此不声称不存在未来推送冲突。

当前 Docker 仍是上次部署版本。后续集中更新会员、PC、H5 镜像并联调，再按用户指示决定是否提交。不能把工作区开发完成等同于当前 Docker 页面已更新。

文档核验：按 find-docs 技能查当前路由/图片选择 API，Context7 连接失败后使用官方文档核对异步路由守卫和 H5 文件类型；未因此升级依赖。
- https://router.vuejs.org/guide/advanced/navigation-guards.html
- https://uniapp.dcloud.net.cn/api/media/image.html
