# 文件访问控制 · 复评整改验收记录（第二轮） — 2026-09-24

> 上一轮：`docs/file-access-control-20260924.md`（③ 安全修复，48/48）。本轮针对复评提出的**五项具体缺陷**做整改。
>
> **后续**：本轮的方案在第二次复评中又暴露 5 项缺陷（绑定上传者只记日志不拒绝导致他人可把私有图抬公开 /
> 同租户任意后台账号可读已绑定私有文件 / H5 仍用原始 URL / 会员登录未测通且 C15 无条件通过 /
> 询价绑定未检查返回结果）。整改与验收见 **`docs/file-access-control-review3-20260924.md`**（回归 48/48）。
> 本文件中 §2 的判定顺序（第 7 步"同租户 BOUND 可读"）已在第三轮删除，以 review3 为准。
>
> **结论：回归 `deploy/mall/verify-file-access.py` 25/25 PASS；公开面 189/189 匿名 200（不回退）；`infra_file` 存活 241 行回到基线（公开 189 / 私有 52）。**

---

## 1. 复评五点 → 处置对照

### ① 登记接口冒领漏洞（`/app-api|/admin-api/infra/file/create`）

| | 问题 | 修复 |
|---|------|------|
| 客户端可传 `configId` + `path` | 任意登录用户拿别人的 path 调一次 create，就把**自己**记成上传者 | 关闭该路径：`createFile(FileCreateReqVO)` 重写 —— 未登录直接拒绝；**同归属**（类型+编号）才幂等返回；**异归属**抛 `FILE_REGISTER_OWNER_CONFLICT(1001003011)`；**全新登记必须消费服务端下发的一次性上传凭证** |
| 读取取「最新一条记录」 | 后插入的记录会遮蔽原记录归属 | `FileMapper.selectFirstByConfigIdAndPath`：读取判定锚定**最早（原始登记者）**记录 |
| 归属接受客户端输入 | 上传者可被伪造 | 归属只取自可信登录上下文：`tenant_id / owner_user_type / owner_user_id` 全部来自 `LoginUser` + `TenantContextHolder`，`directory` 仅做 `biz_type` 归一化、**不产生授权效果** |

**一次性上传凭证**（新增，`deploy/mall/sql/008-file-upload-ticket.sql`）：
`infra_file_upload_ticket(config_id, path, tenant_id, owner_user_type, owner_user_id, expire_time)`。
由 `presignPutUrl` 末尾签发（默认 30 分钟），`/file/create` 必须 `consumeUploadTicket` 通过才允许登记。
`FileUploadTicketDO/Mapper` 带 `@TenantIgnore`，`selectValid` **刻意不含 tenant 条件**——凭证本身就是跨动态数据源查询的载体，归属由凭证里记录的 owner 字段决定。

### ② 身份与租户校验放行顺序

`checkReadAccess(configId, path)` 顺序改为（**租户边界先于一切特权**）：

```
1 无文件记录                       → 匿名 401 / 已登录 403
2 公开资源                         → 匿名 ALLOW
3 私有 + 未登录                     → 401
4 私有 + 租户边界不一致              → 403   ← 先于任何特权（含 infra:file:query）
5 上传者本人（owner_user_type + owner_user_id 严格相等；待绑定窗口内）→ ALLOW
6 已绑定单据的参与方（类型:编号 严格相等）                          → ALLOW
7 已绑定单据 + 同租户后台用户                                        → ALLOW
8 同租户 + 持 infra:file:query 的平台运维                            → ALLOW
9 其余                                                              → 403
```

要点：
- **`isOwner` 严格匹配**：用户类型与编号必须**同时**相等。历史数据 `owner_user_type IS NULL` 时**不再按编号命中**，彻底堵住「会员 id=1 ↔ 后台 id=1 互相越权」。
- 第 5 步「待绑定草稿超期」**不再立即返回**，而是继续向下判定：非特权身份（会员）后续步骤都不命中，结果仍是拒绝；但若本人同时也是**同租户且持 `infra:file:query` 的后台运维**（第 8 步），仍可读——否则会出现「同一后台身份走本人分支被拒、走运维分支被放行」的自相矛盾。
- 新增 `isPublicResource(file)` = `access_type=1 || bind_status=2`，统一公开判定并复用于 `bindFiles`。

### ③ 006 历史回填口径不合规（按 `path LIKE 'mall/product/%'` 等**路径前缀**批量公开）

新增 `deploy/mall/sql/009-file-public-recompute.sql`，把公开口径**收敛为唯一证据源**：一个文件只有被「公开面业务数据」**实际引用**，才确认为公开；否则回落私有。

- 公开面 29 列（商品/规格/品牌/分类/店铺/评价/装修/秒杀/会员等级/订单项/售后/配送/头像等，含 JSON 数组与富文本 HTML）汇入 `tmp_public_ref`（`CONVERT(... USING utf8mb4) COLLATE utf8mb4_bin`），以**文件 URL 作为子串**匹配（URL 中的 `_ %` 已转义，`ESCAPE '!'`）；
- 被引用 → `access_type=1 / bind_status=2`；
- 未被引用**且 `biz_id IS NULL`**（未经服务端 RPC 绑定）→ 回落 `access_type=2 / bind_status=0`；
- 历史 `owner_user_type` 仅在编号在 `member_user` / `system_users` 中**唯一可判**时回填；
- 顺带修正了口径冲突：`member_user.avatar` 属公开面（评论区/后台客服会话里 `<img>` 无法带登录态），故 `FileBizTypeEnum.MEMBER_AVATAR` 的 `publicCapable` 由 `false` 改为 `true`，与 009 对齐。

> 维护约定：新增对外展示的图片列时，必须同步补充 `009` §1 与 `FileMapper.countBusinessReferences` 的列清单。

### ④ `bindFiles` 回归（按 URL 找到记录即覆盖 `bizType/tenant/owner/participants`）

`bindFiles` 重写为**逐项核验后再写**：

| 步骤 | 规则 |
|------|------|
| 3.0 引用公开资源 | **直接跳过**（公开资源不降级、不报冲突）。必须无条件跳过：商品图可被评论复用，若按「重绑冲突」拦截会导致会员评价提交 500 |
| 3.1 租户边界 | 文件租户与请求租户不一致 → `FILE_BIND_TENANT_MISMATCH(1001003013)` |
| 3.2 重绑冲突 | 已挂 `bizId` 且请求 `bizId` 不同，且请求方**不是原上传者本人** → `FILE_BIND_BIZ_CONFLICT(1001003012)`；原上传者本人可改挂 |
| 3.3 归属补录 | `tenant/owner/ownerType` **仅在为 NULL 时补录**，绝不覆盖既有归属 |
| 3.4 参与方 | 以 `type:id` 规整进 `bind_participants`（JacksonTypeHandler 存 JSON 列） |

新增错误码：`FILE_REGISTER_NO_TICKET(1001003010)`、`FILE_REGISTER_OWNER_CONFLICT(1001003011)`、`FILE_BIND_BIZ_CONFLICT(1001003012)`、`FILE_BIND_TENANT_MISMATCH(1001003013)`、`FILE_BIND_OWNER_MISMATCH(1001003014)`。

### ⑤ 未接通真实业务 + 前端

**后端：单据落库后调用 `FileApi.bindFile`**

| 模块 | 接入点 | 业务类型 |
|------|--------|----------|
| `txgy-module-trade` | `AfterSaleServiceImpl.createAfterSale` → `bindAfterSaleImages` | `AFTER_SALE_IMAGE`（PRIVATE + bizId=售后单 ID + 参与方=申请会员） |
| `txgy-module-product` | `PurchaseInquiryService.save` → `bindInquiryImages` | `INQUIRY_IMAGE`（PRIVATE + bizId=询价单 ID + 归属创建会员） |
| `txgy-module-product` | **新增** `framework/file/ProductFileBindingService`，统一入口 | `PRODUCT_IMAGE`（SPU/SKU/轮播）、`SHOP_LOGO`、`COMMENT_IMAGE` |
| `txgy-module-product` | `ProductSpuServiceImpl.createSpu/updateSpu`、`SupplierProductService` 编辑分支（直写 Mapper，不走 updateSpu）、`ProductShopServiceImpl.createShop/updateShop`、`SupplierShopService.save`、`ProductCommentServiceImpl` 两处 `createComment` | 同上 |
| `txgy-module-member` | `MemberUserServiceImpl.updateUser`（两个重载）→ `bindMemberAvatar` | `MEMBER_AVATAR`（PUBLIC） |

配套：4 个 server 模块补 `txgy-module-infra-api` 依赖 + `RpcConfiguration` 注册 `FileApi.class`；
`FileAccessTypeEnum / FileBindStatusEnum / FileBizTypeEnum` 三个枚举**从 `infra-server` 收敛到 `infra-api`**（3 个同名旧枚举删除），供各业务模块共用。

**前端：私有图「带 token → blob → 释放」**

- PC（`txgy-mall-web`）：新增 `src/utils/private-image.ts`（`toAppFileApiPath` 归一化 `/admin-api/`、`/app-api/`、裸 `/infra/file/` → 统一走会员前缀；`loadPrivateImageObjectUrl` 以 `requestBlob({ scope:'account' })` 取流并 `URL.createObjectURL`；`revokeObjectUrl` 释放）；`src/components/InquiryItemImage.vue` 重写为 `watch(modelValue, {immediate:true})` 重取 + `onBeforeUnmount` 释放，失败回退 `normalizeImage`。
- 后台（`txgy-ui-admin-vben/apps/web-antd`）：新增 `src/utils/private-image.ts` + `src/components/private-image/`（`PrivateImage` 组件：`watch(src)` 重取、`Spin` 加载态、失败提示「凭证图片加载失败」、`onBeforeUnmount` 释放）；`views/mall/trade/afterSale/detail/data.ts` 的 `applyPicUrls` 由 `Image` 改为 `PrivateImage`。
- H5（`txgy-mall-uniapp-master`）：`s-uploader` 新增 `directory` prop 并透传给 `chooseAndUploadFile`；上传目录收敛为 `mall/aftersale`（售后凭证）、`mall/comment`（评价）、`member/avatar`（头像）、`mall/withdraw`（提现收款码）、`mall/customer-service`（客服工具面板）。

**「已保存业务图片超过一小时仍可读、不被孤儿清理误删」**

| 机制 | 实现 |
|------|------|
| 可读窗口 | 待绑定窗口 `unboundWindowMinutes` **60 → 1440 分钟**；本人上传的草稿在 24h 内仍可读（>1h 要求满足） |
| 绑定后可读 | 提交成功的业务单据经 `bindFile` → `BOUND`，本人 / 参与方 / 同租户后台长期可读 |
| 不被误删（三重保守） | ① 候选仅 `bind_status=UNBOUND` **且** `bind_expire_time IS NOT NULL` **且** `owner_user_id/owner_user_type` 非空；② 历史行 `bind_expire_time IS NULL` **永久不在候选**；③ 执行前经 `countBusinessReferences(url)` **29 列业务引用二次护栏**，命中即跳过；查询异常时按「疑似被引用」处理（宁可漏删） |

---

## 2. 回归用例（`deploy/mall/verify-file-access.py`，25/25 PASS）

真实 HTTP 通道（本地联调网关 `http://localhost:48080`）+ 真实数据库状态；身份全部来自真实账号登录：
`admin(1, tenant1, super_admin)`、`comency(300030204, tenant1, **无** infra:file:query)`、`xibeihengtai(80000, tenant2001, 有 infra:file:query)`。

| 用例 | 覆盖点 | 结果 |
|------|--------|------|
| C0 | ID 4 仍为 master（storage=1）、ID 36 仍为非 master | PASS |
| C0b | 负例主体 `comency` 确无 `infra:file:query`（保证负例有效） | PASS |
| C1 | **公开资源匿名可读全量回归 189/189**（不回退） | PASS |
| C2 / C2b | 上传一律落「私有 + 待绑定」；tenant/owner 取自登录上下文 | PASS |
| C3a | 匿名读私有 → 401 | PASS |
| C4 | **已保存图片超过 1 小时，本人仍可读（200）** ★⑤ | PASS |
| C4b | 超期待绑定草稿仍对匿名拒绝（401） | PASS |
| C4c | 超期待绑定草稿对同租户持 `file:query` 后台仍可读（200，走运维通道而非「本人」分支）——**用于确认线上跑的是本轮新逻辑**（旧实现会在此处 403） | PASS |
| C5 | **同 ID 跨身份不放行**：`owner_user_type=MEMBER` + 同编号 → 403 ★② | PASS |
| C5b | 对照：`owner_user_type=ADMIN` + 同编号 → 200 | PASS |
| C6 | 历史数据 `owner_user_type=NULL` 不按编号放行 → 403 ★② | PASS |
| C6b | 同租户 + 持 file:query 的运维通道仍可读 → 200 | PASS |
| C7 | **跨租户后台（持 infra:file:query）读本租户私有 → 403** ★② | PASS |
| C7b | 对照：本租户归属者可读 → 200 | PASS |
| C8 | 匿名调 `/file/create` 被拒（框架 401） | PASS |
| C8b | 已登录但无凭证的新登记 → `NO_TICKET(1001003010)` | PASS |
| C9 | **他人 path 重新登记 → `OWNER_CONFLICT(1001003011)`** ★① | PASS |
| C9b | 原记录归属未被遮蔽/改写（仍是原 id + 原 owner）★① | PASS |
| C10 | 本人重复登记同一 path 幂等成功 | PASS |
| C11 | 过期未绑定且无业务引用 → 被清理（正例，证明清理在工作） | PASS |
| C12 | **过期未回绑但被业务引用 → 不被误删** ★⑤ | PASS |
| C13 | 历史行（无过期时间）不在清理候选内（清理前后 52 → 52） | PASS |
| C14 | **公开集合中「无业务引用」的记录数 = 0**（口径 = 真实业务记录）★③ | PASS |
| C15 | 会员通道可驱动性声明（见 §4 环境夹具） | PASS |

**数据库基线**：`infra_file` 存活 241 行（公开 189 / 私有 52）、软删回到基线、`fileacl/` 测试残留 0、`infra_file_upload_ticket` 0。回归脚本自带清理，可重复运行。

---

## 3. 部署

**后端**（源码在本机 Reactor 构建，用「补丁镜像」仅替换 `app.jar`，保留线上 JVM 参数 / 工作目录 / 健康检查）：

```bash
# 1) 本机构建（infra-api 改动会被正确打进各服务 fat jar）
mvn -o -B -DskipTests -pl txgy-module-infra/txgy-module-infra-api,\
txgy-module-infra/txgy-module-infra-server,txgy-module-mall/txgy-module-trade-server,\
txgy-module-mall/txgy-module-product-server,txgy-module-member/txgy-module-member-server -am package

# 2) 逐个服务打补丁镜像（Dockerfile.patch-app / Dockerfile.patch-product）
#    product 的 jar 在 /txgy-module-product-server/app.jar，其余在 /app/app.jar

# 3) 叠加覆盖层上线
docker compose -p deploy \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mall-rollout.yml \
  -f deploy/docker-compose.mall-android-rollout.yml \
  -f deploy/docker-compose.file-acl-rollout.yml \
  -f deploy/docker-compose.file-acl-frontend.yml \
  up -d --no-build infra trade product member mall-pc mall-h5 frontend
```

**镜像清单**（`--no-build` 必带：主编排里这些服务都带 `build:` 段，否则会在容器内重跑整条 Maven Reactor）：

| 服务 | 上线镜像 | 基底（回滚用） |
|------|----------|----------------|
| infra | `deploy-infra:file-acl-20260924-1` | `deploy-infra:appapi-20260924` |
| trade | `mall-trade:file-acl-20260924-1` | `mall-trade:cross-tenant-refund-20260923-3` |
| product | `mall-product:file-acl-20260924-1` | `mall-product:opening-stock-20260923-1` |
| member | `mall-member:file-acl-20260924-1` | `mall-member:address-map-20260922-1` |
| PC / H5 / admin | `txgy-mall-pc\|h5\|admin:file-acl-20260924-1` | `…:android-102-20260924-2` / `mobile-fixes-20260924-2` / `pc-entry-20260924` |

**回滚**：去掉 `-f docker-compose.file-acl-rollout.yml -f docker-compose.file-acl-frontend.yml` 后 `up -d --no-build <服务>`，即回到上表「基底」镜像（容器重建，数据库无需回滚）。

**数据迁移**：`008-file-upload-ticket.sql`（建凭证表，全局表）、`009-file-public-recompute.sql`（公开口径重算；`UPDATE` 均为幂等重算，可重复执行）。

---

## 4. 遗留与说明

1. **④ 重绑冲突的黑盒验证受限**：`FileApi.bindFile` 走 `/rpc-api`，不经网关暴露，无法用 HTTP 直接构造「已绑定 A 单据的文件再绑 B 单据」。本轮以**代码走查 + C12 引用护栏**为证据；建议后续补一个 infra-server 集成测试（直接调 `FileServiceImpl.bindFiles`）覆盖 `FILE_BIND_BIZ_CONFLICT / TENANT_MISMATCH`。
2. **会员通道回归待夹具就绪**：当前联调库缺少新版商城认证夹具（`member_platform_account` / `member_platform_account_link` 无数据），`/app-api/member/mall-auth/login` 返回「商城入口暂不可用」，会员端登录不可用。会员归属通道的**等价安全属性**已由 C5 / C6 用「同租户但无 `infra:file:query` 的后台身份」覆盖。修复夹具后建议补跑会员侧用例（本人 200 / 他人 403 / 跨租户 403）。
3. **H5 未做 blob 展示**：H5 售后凭证属**本人资源**且窗口期 1440 分钟、上传后立即绑定，`<image>` 直连原始 URL 已满足「>1h 仍可读」；如需彻底避免 URL 泄露，后续可统一改为 blob。PC 与后台已实现 blob。
4. **前端产物重建的构建期变量**：PC 镜像此前是带外构建、参数未入库。本轮按 `deploy/mall/compose.yaml` 的 canonical 取值重建：
   `VITE_PAYMENT_PREVIEW=true`、`VITE_MALL_PUBLIC_BASE_URL=http://localhost:15175`、`VITE_MALL_DEMO_MODE=true`、`VITE_MALL_H5_PORT=3000`、`VITE_PUBLIC_IMAGE_ORIGIN=<OSS 公开源>`；安卓下载信息运行时从 `public/downloads/android-release.json` 读取（已从线上容器回同步到源码 `public/downloads/`，避免丢产物）。**建议后续把 PC/admin 的构建参数正式写回 compose，消除带外构建。**
5. **① 公开图按服务端白名单走 OSS**：按约定「验收后再做」，本轮未动；ID 4 仍为 master（storage=1 即 DB 存储）、ID 36 仍为非 master（OSS）。
6. **框架既有行为（未改动）**：会员 token 请求 `/admin-api/**` 会被 `TokenAuthenticationFilter` 判「错误的用户类型」。前端一律走 `/app-api` 规避。

---

## 5. 复现

```bash
# 文件访问控制回归（23 用例，需要宿主机能访问 docker 与 48080 网关）
python deploy/mall/verify-file-access.py

# 容器烟测（PC/H5/admin HTML、公开目录与 SKU、公开 OSS 商品图、管理员登录）
cd deploy/mall && python verify.py   # 或在容器内 docker compose run --rm verify
```
