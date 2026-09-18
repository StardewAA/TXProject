# 会话恢复、主商城入口与 H5 企业资料集中部署（2026-09-16）

## 本次结果

按用户“更新 Docker，检查未提交代码，没问题就提交；今天先到这里”的要求，将基于本地 master e181ce84a 的两批开发集中验证、部署。仅涉及商城 PC、H5、会员服务的 mall tenant 配置/目录及相关测试、文档；没有改后台管理、ERP、WMS 或共享框架源代码。

- 会员：mall-member:scoped-entry-20260916-1 → deploy-member-1。
- PC：mall-pc:scoped-entry-20260916-1 → txgy-mall-web-integration，http://localhost:15175/。
- H5：mall-h5:scoped-entry-20260916-1 → deploy-mall-h5-1，http://localhost:3000/。
- 后台原镜像保持不变，http://localhost:5173/；网关 http://localhost:48080/。
- 仅重建上述三个容器，其余原有商城依赖服务启动但未重建。未动独立项目的 Redis。
- 原镜像与所有数据卷保留；没有执行业务 SQL、初始化租户、改账号密码/权限或使用真实验证码会话。

## 复核与验证

| 项目 | 本次结果 |
| --- | --- |
| PC 自动测试 | 327 项通过 |
| H5 自动测试 | 228 项通过 |
| member 定向测试 | 185 项通过，无失败/错误/跳过 |
| 自动测试合计 | 740 项；不计入未复跑的历史 product 后端测试 |
| 编译 | PC 类型检查/生产构建、H5 生产构建、member 离线 Maven clean package 成功 |
| Docker 产物浏览器回归 | 67 组通过：启动恢复 12、认证 8、注册 10、匿名商城浏览 8、客户归属 16、入口/企业资料 13 |
| 真实服务只读部署检查 | 17 项通过，含镜像 ID/JAR/HTML/JS/CSS 校验、接口和开关检查 |
| 真实匿名页面检查 | PC/H5 注册页面可用，无脚本错误、未发送写请求 |
| 业务服务健康 | member、product、trade 的 actuator/health 返回 UP |
| 差异边界 | 没有新增依赖或 SQL；没有修改商城范围外源代码 |

67 组浏览器测试使用 Docker 中实际前端产物，但所有业务 API 被隔离响应拦截，绝不把它们称为真实双租户交易验收。真实页面只读检查未提交注册、未发送短信、未使用会员 316/admin 会话。H5 构建有现有 Sass 弃用警告，无构建错误。

## 现行安全状态与后续范围

实际接口确认多租户目录、匿名预览、主入口 scopedEntry、browseSwitch 和注册租户选择均未启用。默认仍走租户 1；新公共租户和公开/注册名单未确认，不建立 3000、9001、9002 等测试编号对应的真实数据。

本批完成启动会话核验、入口与令牌隔离、H5 企业资料提交、PC 结算草稿隔离等，**不等于多租户全链路上线**。所有业务页面/新窗口链接、客服实时消息与附件、支付/地图/物流资源、真实双租户下单到售后以及老账号完整回归，仍需后续按方案完成。

没有查看远程仓库，因此不对未来推送冲突作保证；本次仅按用户授权提交本地 master，不推送。今天不再继续扩展功能。

## 证据与回退

证据目录：E:/txgy/local-backups/mall-scoped-entry-deploy-20260916/

- pc-tests.log / h5-tests.log / backend-package.log；pc-build.log / h5-build.log。
- images/member、images/pc、images/h5：镜像配方及实际打包产物。
- docker-member.log / docker-pc.log / docker-h5.log。
- 六个浏览器脚本、各 JSON 报告与页面截图。
- smoke.cjs / smoke-result.json；anonymous.cjs / anonymous-result.json。
- compose.before.json：更新前的完整部署配置备份，仅本机保存，不纳入 Git。

实际部署配置：E:/txgy/local-backups/docker-organize-20260913/compose.current.json。本次经结构比较仅三个 image 值发生变化，端口、挂载、环境配置均保留。必要时经确认将这三个镜像分别改回 mall-member、mall-pc、mall-h5 的 market-auth-20260916-1，再仅重建对应服务；不删除数据卷，不整体 down。

本轮使用 find-docs 技能核对选择性更新参数；Context7 额度不足后查阅 Docker 官方 Compose up 文档，使用 --no-deps --no-build --pull never 更新指定服务，不清理其他容器或镜像。
