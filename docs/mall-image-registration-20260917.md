# 商城 PC/H5 简化注册（2026-09-17）

## 本轮范围

依据 `E:/JAVA/project666/商城多租户与智能服务升级开发方案_2026-09-15/注册客服与外部服务范围调整_2026-09-17.md`，实现登录名、密码确认、服务器图片验证码注册。采购商/供应商选择、协议确认、企业资料提交与后台审核不变；注册本身不授予企业采购/销售权限。

仅修改商城 PC、H5、会员服务、后台会员页面，以及对应增量 SQL/测试。未改 ERP/WMS、共享认证框架或系统租户管理。未访问远程 Git，未提交。

## 规则与接口

- 登录名 trim + ASCII 小写，6–20 位，字母开头，后接字母/数字/下划线；同租户唯一。软删除后名字继续保留，旧会员该字段保持 NULL。
- 新密码 6–16 位，现有 BCrypt 散列；旧手机号密码登录仍允许既有 4–16 位密码，不修改任何老会员密码。
- 可选联系手机另存 `contact_mobile`，不填入身份字段 `mobile`，不用于登录/重置。不造假手机、不认领相同手机的旧账号。
- `GET /app-api/member/mall-auth/config` 增加 `registrationMethod=image-v1`、`loginNameEnabled=true` 能力；旧 `protocol` 保持兼容。能力关闭/缺失时两端不降级为短信新注册。
- `POST /app-api/member/mall-auth/image-challenge`：tenantId、loginName、captchaSession、previousChallengeId（可选）、purpose=REGISTER。
- `POST /app-api/member/mall-auth/register-image`：tenantId、loginName、password、confirmPassword、captchaSession、imageChallengeId、imageCode、contactMobile（可选）、nickname（可选）、memberIdentity、agree。
- `POST /app-api/member/mall-auth/account-login`：tenantId、account、password。account 为新登录名或老身份手机号。
- 图片为服务端 PNG，5 位字母/数字，300 秒有效、2 秒刷新、最多 5 次错误、一次消费；绑定 tenant/name/session/purpose。刷新同 session 时旧图立即不可用，即使切换了租户。`previousChallengeId` 不授权删除其他会话。
- Lua 原子管理验证与限流；租户切换不能重置账号/session/IP 总预算。窗口 15 分钟，签发与验证各为账号/session 30、连接 IP 300；密码账号 30、连接 IP 300。经过网关时连接 IP 是网关连接地址，属于保守聚合限流，不能据此声称已实现可信代理后的独立终端 IP 配额。
- Redis `mall:image:{mall-image}:hmac-secret` 通过安全随机数 SETNX 生成共享密钥，无短信模板依赖。保护 Redis 访问；丢失/更换密钥后旧图片失效，客户端刷新即可。不得开放读取答案/测试跳过验证接口。
- 原短信注册及 SMS/社交/微信自动开户在图片模式下禁用；已存在且持有原身份凭证的账号仍可登录。普通图片不能替代短信登录、手机绑定或找回凭证。

## 数据迁移与部署

1. 先备份会员表，执行 `deploy/mysql/repair/2026-09-17-member-image-registration.sql`。仅加 `login_name`、`contact_mobile` 和 `(tenant_id,login_name)` 唯一索引，保留原手机号索引。
2. 代码配置 `mall.registration.picture-enabled` 默认 false；数据库迁移后才在本机 Compose 给 member 配置 `MALL_REGISTRATION_PICTURE_ENABLED=true`。
3. 不启用 `mall.tenants.enabled` / `mall.auth.enabled`，不创建公共租户或发布租户名单，入口仍为租户 1。
4. 更新会员后端、PC、H5及后台会员页面镜像。保留旧镜像和数据卷。
5. 优先保留增量列并修复前进。关闭新能力时，新注册关闭，旧手机号入口仍可用，但已创建的登录名账号不能使用旧镜像登录；有新账号后不能简单回滚到旧会员镜像。数据库回退脚本遇到非空新字段会拒绝删除。

本机证据目录：`E:/txgy/local-backups/mall-image-registration-20260917/`。该目录包含私密数据备份，不纳入 Git，不公开分享备份或 Compose 中的凭据。

## 已验证与剩余边界

- MySQL 增量重复执行、唯一约束、同名跨租户、NULL 老数据、软删除名字保留、防误回退和重新应用已通过。
- 实际库原 148 条会员记录旧字段的 SHA-256 校验迁移前后一致，未注册真实测试会员、未改老密码/归属。
- 独立 Redis 验证 19 项通过，测试用临时 Redis 已清理，未清空项目 Redis。
- 后端真实事务测试覆盖用户/身份/客户归属原子写入、提交后会员创建消息、失败回滚、并发唯一、BCrypt 与手机号兼容。
- PC/H5 全 API 模拟浏览器测试与实际 Docker 匿名接口检查分别记录，不能把模拟成功说成真实账号已注册。没有临时验证码/真实账号授权时，不借用会员 316 或 admin 会话。
- 新三个认证入口的参数绑定/JSON 异常由本地处理，避免全局异常日志回显凭据；会员服务的请求体控制台日志改为 WARN。实际容器测试密码哨兵扫描为 0。此结论不覆盖旧认证接口全部畸形输入路径：旧接口的全局异常日志、短信字段脱敏还需另做审计，不能宣称全站凭据日志风险已清零。
- **U-07 人工申诉后的安全找回尚未完成**：要另做核验权限、一次性短期凭证、完整审计及旧访问/刷新令牌可靠撤销。现有共享网关令牌缓存及刷新令牌清理仍有待处理边界，本轮不越界修改共享安全模块。未经验证的联系手机不可用于找回。既有短信找回只针对原身份手机号。
- 真实短信、物流/地图授权、真实 AI 等不在本轮启用；客服首版的后续工作不计为本轮注册完成。

最终测试数量、镜像标记及健康结果见同目录 `mall-image-registration-acceptance-20260917.md`。
