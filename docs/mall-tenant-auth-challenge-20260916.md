# 多租户认证挑战：后端批次交接（2026-09-16）

## 基线与范围

上一批租户目录、双端会话隔离基础已经按用户要求提交到本地 master：`ed55da8e8`（`feat(mall): 增加租户入口与双端会话隔离基础`）。未访问远程，未推送。

本批在上述提交后接续开发，仅修改 member 模块及本文档。完成独立验证码挑战、租户认证入口和旧入口服务端开关；不是完整多租户上线。没有修改公共 system、framework、gateway、其他商城服务及前端代码，没有执行业务 SQL 或初始化租户。

## 已实现

- 独立 Redis 命名空间 `mall:auth:{mall-auth}:`。SecureRandom 六位验证码；挑战与租户、手机号、用途、随机 challengeId 绑定，Redis 只存 HMAC 摘要，不存验证码/手机号明文。短信服务自身的模板日志仍由原有短信系统管理，不能声称整个系统没有明文短信内容。
- Lua 原子预留、置可消费、验证消费和取消；默认 300 秒过期、60 秒重发、同挑战最多 5 次输错。并发消费只允许一次成功，重放、过期、跨租户/手机号/用途均拒绝；错目标不会删除原挑战。
- 手机号发送与验证预算跨租户/用途共享；peer 发送和验证预算、密码登录预算。所有 Lua 键有同一 Redis Cluster hash tag；这是正确性优先的单槽设计，不代表无限水平扩展能力。
- 短信 RPC 失败、超时或日志 ID 无效时作废挑战，不返还防刷预算。返回 `SUBMITTED_UNCONFIRMED` 仅代表提交记录，不能当作运营商实际送达。
- 每次发码和消费重新核验目录白名单及租户有效状态。省略租户只取配置的公共租户，配置缺失不回退 1；请求头存在时必须与目标一致。注册还需该项明确允许注册。
- 注册、短信登录、密码登录、重置密码使用显式请求白名单，不接收企业角色/员工绑定等越权字段。短信登录只允许已有账号，不悄悄开户。注册身份只是 buyer/supplier 的申请方向，不自动授予企业权限。
- 重置密码校验当前租户内有效会员，更新 BCrypt 密码并调用现有令牌撤销接口。撤销失败时本地密码事务回滚，已消费验证码不恢复。
- 新控制器不记录请求/响应正文；基础设施异常转成不含凭据的业务错误，避免进入现有通用未知异常正文审计。保留不含正文的操作审计，不修改共享日志框架。

## 接口约定

前缀：`/app-api/member/mall-auth`，均为 POST JSON。目标 `tenantId` 可以省略；`mobile` 必填。

| 路径 | 其他请求字段 | 行为 |
| --- | --- | --- |
| `/send-code` | `purpose=REGISTER/SMS_LOGIN/RESET_PASSWORD` | 返回 challengeId、可信 tenantId、expiresIn、retryAfter、deliveryStatus；不返回 code |
| `/register` | challengeId、六位 code、password、nickname、agree=true、memberIdentity=buyer/supplier | 仅 REGISTER 挑战，完成既有会员注册与会话签发 |
| `/sms-login` | challengeId、六位 code | 仅 SMS_LOGIN 挑战，不创建新会员 |
| `/login` | password | 租户内密码登录，账号沿用手机号登录规则 |
| `/reset-password` | challengeId、六位 code、password | 仅 RESET_PASSWORD 挑战，成功返回 true |

密码按现有注册规则 6～16 字符；nickname 必填且最多 30 字符。前端收到重置成功后应要求重新登录。发码返回秒数是配置值，服务端 TTL 从预留时开始，前端倒计时不能延长服务端有效期。

## 开关与启用门槛

`mall.tenant.enabled` 与新增 `mall.tenant.auth.enabled` 均默认 false，未写入运行配置。

新增配置类 `MallTenantAuthProperties`：HMAC 密钥必须至少 32 UTF-8 字节，实际使用随机强密钥；短信模板 code 必填；默认 mobileDailyLimit=10、ipHourlyLimit=100、mobileVerifyLimit=30、ipVerifyLimit=300。验证码及密码尝试窗口为 15 分钟，发码手机号窗口为首次计数起 24 小时，peer 发码为 1 小时，不是自然日统计。

不要把真实密钥或渠道配置提交到仓库。密钥轮换会使现有挑战失效，同时改变预算键，应在运维窗口处理，不能作为解除限流的方法。

只启用目录不会封闭旧认证。当 **auth.enabled=true** 时，member 自己的拦截器封闭旧 auth（logout/refresh-token 除外）、旧 social-user、旧修改/重置密码和换手机号入口。即使新协议配置不完整，旧入口也不会自动重新放行。不能只开启这个开关就上线：会影响仍调用旧接口的客户端。

启用前仍须完成：

1. PC/H5 接入挑战、新登录/注册/忘密、租户选择与注册选择；处理旧社交/小程序登录、换手机号等入口（本批封闭旧入口，没有交付新替代流程）。
2. 公共客户归属独立落库及生命周期，不将客户关系转成平台企业员工/采购/销售权限；本批未新增该关系，也未自动绑定平台企业。
3. 用户确认新公共租户、平台自营企业、公开/注册白名单和商品来源后再初始化。为旧账号保留其合法租户入口，不擅自迁移。
4. 真正多租户账号/商品/订单/分享/购物车/上传下载隔离联调，完整客户端能力通过后再开放目录 capability；当前两个 capability 仍为 false。
5. 配置并验证短信模板、渠道与送达结果。当前不启用真实短信，不能仅因发送 RPC 返回 ID 就宣布接通。

生产边界：peer 取 servlet remoteAddr，不信任客户端自行填写 X-Forwarded-For。通过网关时可能聚合成网关地址，启用前需确认可信代理与容量；不能直接改信任任意转发头。现有网关令牌缓存的短时失效延迟未修改；密码与远端令牌撤销没有分布式原子事务，回滚可保证本地密码不误更新，但不能恢复已撤销远端会话。

## 验证结果

- 后端选定 23 模块离线 `clean package` 成功；120 项测试，0 失败/错误/跳过。含既有积分 45、旧租户认证 10、目录 9、目录控制器 4，本批新增 52 项。
- 新增覆盖：策略 9、旧入口拦截 19、验证码服务 9、认证服务 7、H2 真实事务/租户插件 4、控制器校验/日志边界 4。H2 验证重置只改目标租户、撤销失败回滚、停用/删除会员拒绝、软删除唯一键冲突。
- 真 Redis Lua 14 项通过，其中 20 个并发消费仅 1 个成功。测试通过独立 random-ID、无网络、无端口、无数据卷 Redis 容器执行，结束只清理自己的测试容器，不读写项目 Redis。
- 实服 12 项冒烟通过：5 个新接口关闭时在任何业务写入前拒绝；旧登录仍到参数校验；匿名目录保持 enabled=false、legacyTenantId=1；PC/H5 HTTP 可访问；业务摘要不变；Compose/JAR 一致性通过。
- 另外以全新匿名浏览器上下文验证 PC/H5 首页实际渲染，两个页面均无运行时异常并保存截图；会员服务 actuator health 为 UP。没有用首页通过代替登录/交易全链路。
- 本批没有使用会员 316/admin 临时登录权限，没有发真实短信、创建业务用户、修改密码或授权。开关关闭实服冒烟不等于启用后的完整多租户 E2E。

复跑后端：`mvn -o -B -ntp -pl txgy-module-member/txgy-module-member-server -am -Dtest=Mall*Test,AppMall*Test,MemberAuthTenantTest,MemberPointRecordIdempotencyTest -Dsurefire.failIfNoSpecifiedTests=false clean package`。

复跑真实 Redis：`node --test txgy-module-member/txgy-module-member-server/src/test/scripts/mall-auth-redis.test.cjs`（需要已运行 Docker 及本机已有 redis:7-alpine 镜像）。

## Docker 与交付

只更新 `deploy-member-1` → `mall-member:tenant-auth-20260916-1`。镜像与容器 JAR SHA256：`3e4ef0513d99f742e6482e6f28cc6f5fca4ce6db927b4fe8b131feb3588bd70c`。PC/H5 保持上一批 `tenant-foundation-20260916-1`，端口 15175/3000，网关 48080。

私有 Compose 前后比较仅 member.image 改变；网络、端口、数据卷、配置及其他服务不变；保留旧镜像可回退。订单、订单项、售后、支付、积分、SKU 库存摘要一致。

证据：`E:/txgy/local-backups/mall-tenant-foundation-20260916/auth-package.log`、`auth-redis-tests.log`；`E:/txgy/local-backups/mall-tenant-auth-20260916/` 中 Docker 构建/部署日志、smoke-result.json、browser-result.json、双端截图和 Compose 私有备份。私有文件不入 Git。本批修改待用户决定提交，未自动提交第二次。

开发查证使用 find-docs；Context7 配额不足，回退官方 [Redis Lua 原子性](https://redis.io/docs/latest/develop/programmability/eval-intro/) 与 [Spring Data Redis 脚本执行](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)。据此采用原子消费与同槽显式 KEYS，没有以应用层先查后删替代并发安全。
