# 公共商城客户服务归属：开发、数据库部署与联调（2026-09-16）

## 基线与范围

基线为本地 master `c81dd3b1d`，开始时工作区干净；未访问远程。本轮按“继续开发与本地测试”接续多租户方案第 3.2 节，只完成公共注册客户关系的后端基础，不宣称多租户切换或整套方案已上线。

仅修改商城 member、product-api/product-server、对应测试和增量 SQL/文档。不修改 system、共享 framework、ERP/WMS、后台页面或前端。初始开发阶段只做本地测试；随后用户明确授权更新 Docker、执行脚本、联调通过后提交本地 master。最新部署情况见末节，初始阶段的“未部署”说明不代表当前状态。

## 已实现

1. 新增 `member_mall_customer_affiliation` 独立关系：tenant_id、member_id、service_enterprise_id、source 和审计字段。该表不保存企业员工/采购/销售/店铺角色，不参与这些权限判断。
2. 新增服务端配置 `mall.tenant.platform-enterprise-id`，默认空。仅在新认证开启、目标等于明确配置的 publicTenantId 时建立平台客户归属。显式选择公共租户和后续未选租户解析到公共租户，使用同一规则；其他租户不自动归到平台企业。客户端不能指定服务企业。
3. product 增加内部只读 `GET /rpc-api/product/enterprise/customer-owner?id=...`（完整前缀以 Product ApiConstants 为准），只返回企业 ID、可信租户、名称及审核是否通过；不返回营业执照、信用代码、联系方式。无租户/忽略隔离/跨租户/删除企业均不返回资料。
4. 公共注册发码和消费的策略预检会验证平台企业；正式绑定前再次查询，要求同租户、正确 ID、审核通过。配置缺失、企业不存在/跨租户/审核失效、RPC 故障均拒绝，不回退租户 1 或任意企业。现有账号登录/刷新不依赖客户服务企业是否可用。
5. 会员注册与客户关系写入加入同一 Spring 本地事务，关系服务要求调用方已有事务；先保存归属，再执行登录日志和令牌 RPC。关系失败不会签发新令牌，并回滚新会员和其身份字段。验证码已消费时不恢复。
6. 先锁定当前租户的有效会员，再锁/写关系；唯一键 `(tenant_id, member_id)` 不包含 deleted。并发/重试不重复落库；配置变更不能重绑已有客户，逻辑删除关系不能通过注册恢复或覆盖。没有普通用户可调用的新增、重绑、删除关系接口。
7. `auth.enabled=false` 时直接跳过新关系查询和企业 RPC，旧入口不要求新表已存在。未给存量账号补绑定，不改其 tenant_id，不复制商品。
8. 增加 member 对 product-api 的依赖及显式 Feign 客户端注册；不跨服务直接读取 product 表，不向 product 写企业/店铺/审核关系。

## 本地验收

| 验证 | 结果 |
| --- | --- |
| member 选定回归 | 151 项通过，含积分幂等、原认证、租户目录/策略、挑战、客户归属 |
| product 全部现有 Java 测试 | 104 项通过，含企业权限、审核、销售中心、询价导入、WMS 只读边界 |
| PC 单元测试 | 188 项通过；本批前端无源码变化 |
| H5 单元测试 | 96 项通过；本批前端无源码变化 |
| 合计 | 539 项，0 失败/错误/跳过，不累计重复运行次数 |
| 构建 | member/product 及所需依赖离线 clean package 成功 |

新增客户归属单测 12 项、真实本地事务测试 13 项、product 最小投影测试 4 项；另补注册预检策略 2 项和无企业申请不得采购/销售 1 项。共新增 32 项。

真实事务测试使用独立随机命名 H2、实际 Spring 事务代理、真实 member/关系 Mapper 和租户拦截器；外部企业读取、会员创建的上层编排及 OAuth RPC 使用测试替身。覆盖实际注册编排中先落关系再请求令牌、关系表缺失/企业失效/RPC 失败回滚、并发八次只落一行、同手机号不同租户不迁移、删除/配置更改不覆盖、旧注册入口无需新表。不是线上 MySQL/跨服务真实 HTTP 联调，也不是完整分布式事务证明。

本地证据：`E:/txgy/local-backups/mall-customer-affiliation-20260916/` 的 backend-package.log、pc-tests.log、h5-tests.log；早期 backend-tests.log 为中间测试记录，最终结果以 clean package 日志为准。

复跑后端：

```text
mvn -o -B -ntp -pl txgy-module-member/txgy-module-member-server,txgy-module-mall/txgy-module-product-server -am -Dtest=Mall*Test,AppMall*Test,MemberAuthTenantTest,MemberPointRecordIdempotencyTest,EnterpriseMembershipServiceTest,Supplier*Test,Product*Test,WmsReadonlyBoundaryTest,Inquiry*Test,PurchaseInquiryServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean package
```

## 数据脚本与启用边界

新增 `deploy/mysql/repair/2026-09-16-mall-customer-affiliation.sql`，只有建表和索引，无业务数据初始化、租户种子或角色授权。初始本地开发阶段未执行；后续已按明确授权经备份、真实隔离 MySQL 验证后执行到本机项目库，见末节。`IF NOT EXISTS` 不验证已有同名表结构，其他环境仍须先核对结构，不能直接重复发布当作迁移校验。

新认证和多租户能力继续关闭；本机 member/product 已更新至本批镜像，入口仍是租户 1。新公共租户、平台企业、公开/注册名单及商品来源仍待用户确认；不硬编码 1/3000，不自行创建。

后续集中发布应包含 product 新查询接口与 member，并先验证 product 接口及建表，再允许新注册能力启用。回退时先关闭新认证，保留关系表和历史记录，不 DROP、不将客户转成员工。

本轮只落注册初始关系与防覆盖约束：尚未提供会员侧关系展示、后台客户管理、资质撤销后的服务状态展示、关系变更/撤销审批及其审计流程。不能用本表赋予企业权限，也不能把其存在视为企业资质已认证。旧企业申请审核及销售店铺授权保持原规则。

本地事务不能与远端 OAuth/日志实现原子提交；令牌 RPC 已成功但本地提交随后异常的既有跨服务风险，本批未声称消除。后续上线前需要结合真实服务验证补偿/会话可用性；本轮只保证归属写入失败发生在签发令牌之前。

## 接续顺序

1. 补服务归属的本人只读展示与失效状态，不提供自动企业授权。
2. 双端认证目标/注册可清空租户选择、公共注册确认提示；复用现有挑战绑定。
3. 首页浏览切换及全资源范围、分享、私有请求、购物车/结算跨租户校验。
4. 独立双租户端到端验收，再申请公共租户/企业初始化与开关启用。

开发使用 find-docs 核对事务；Context7 配额不足，回退 [Spring 官方事务文档](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)。因此将关系写入放在独立受代理 Bean 中、使用 MANDATORY 并通过真实 Spring/H2 测试验证代理生效和回滚，而不是只靠方法上的注解宣称事务已生效。

## 2026-09-16 用户授权后的数据库部署与实服联调

### 数据库

- 先恢复原来停止的商城 MySQL/Redis；不处理其他项目容器。MySQL 版本 8.4.11，项目库 `ruoyi-vue-pro` 的表均为 InnoDB，目标关系表原来不存在。
- 使用 mysqldump single-transaction/quick 对完整项目库（含触发器、存储程序和事件）做发布前备份，文件 `E:/txgy/local-backups/mall-customer-deploy-20260916/database.before.sql`，458699798 字节。退出码、文件大小及 SHA-256 均已记录；备份含私有数据，不入 Git，未在本轮进行完整备份恢复演练。
- 在本轮随机新建的独立 MySQL 验证库执行原始 DDL，验证表引擎/列/索引、重复执行、租户维度唯一键、跨租户独立、逻辑删除保留唯一约束、事务回滚及 8 个并发插入只成功 1 个。验证通过后在项目库执行脚本，并逐项比较结构。
- 项目库新表保持 0 行；没有初始化租户/企业，没有给存量账号建立客户归属或经营授权。原会员身份/密码摘要、企业、店铺、订单、明细、售后、支付、积分与 SKU 库存摘要一致。
- 临时验证库按本轮唯一名称核验后清理，项目库和完整备份保留。数据库验证共 9 项通过，记录于 database-result.json。安全执行器第一次将列注释里的分号误计成第二条 SQL，在任何数据库改动前停止；修正执行器字符串解析后通过，业务 DDL 无需修复。

### 镜像和测试

- 重新执行 539 项本地测试并 clean package，全部通过，无失败/错误/跳过。
- 更新 `mall-product:customer-affiliation-20260916-1` 与 `mall-member:customer-affiliation-20260916-1`；私有 Compose 只变化这两项 image，端口、环境配置、网络、挂载卷保持不变，旧镜像保留。PC/H5/后台及其他业务镜像未重建。
- member/product 健康端点均 UP，容器镜像 ID 和 JAR 校验匹配。member JAR SHA-256 `1750e3c9fc2e0b3098a7e125946d0443c68d6a2fda215b1c283f494493e1c8f4`；product JAR SHA-256 `ca2a994f7ed68f1264777ef4580afdc40827d07f7a90b9c5612acad47068f530`。
- 14 项部署/接口检查通过：真实 member 容器到 product 的 HTTP 只读查询返回同租户企业最小资料；跨租户/无租户/不存在企业不返回错误归属；协议发现仍 legacy、租户 1，目录和新认证保持关闭；新发码/注册关闭态拒绝且无写入；三端首页 HTTP 可访问，业务摘要不变。
- 用户重新授权本轮会员 316 临时验证码，未复用过去的单轮授权，未使用 admin。14 项真实会话/浏览器回归通过：H5 六位码真实登录、正确租户刷新、错误租户刷新拒绝且原会话保留、PC/H5 读取同账号真实订单、匿名首页及 PC 认证页、非法租户深链保护。
- 没有发真实短信、注册新业务账号、修改密码、下单、支付或改变经营权限。本轮没有开启公共租户注册，不能把兼容态回归与 MySQL 表验证称作“新公共注册已正式启用后的全链路验收”。
- 清理验证：临时验证码删除 1 条、残留 0；注销返回成功，活跃 access/refresh 记录均为 0；等待现有网关缓存失效约 45 秒后，旧测试令牌均被拒绝。

发布证据目录：`E:/txgy/local-backups/mall-customer-deploy-20260916/`，含数据库备份/验证、镜像构建日志、smoke-result.json、真实会话回归结果与截图。私有部署文件、备份、临时会话及测试工件均不入 Git。

部署阶段按 find-docs 回退核对 [MySQL 建表官方文档](https://dev.mysql.com/doc/refman/8.4/en/create-table.html) 和 [Docker Compose 官方文档](https://docs.docker.com/reference/cli/docker/compose/up/)，采用先备份、隔离验证、结构比较，并仅替换指定镜像、保留卷；不把 DDL 当作可随业务事务回滚的操作。

## 2026-09-16 下一批本地接续

“接续顺序”第 1 项本人只读展示与失效状态已在下一批完成本地开发和测试，见 [客户服务归属本人展示](mall-customer-profile-20260916.md)。该批 581 项回归及 16 组接口替身浏览器检查通过，尚未部署/提交；不覆盖上文前一批已部署状态。租户选择、浏览切换与双租户 E2E 仍待接续。
