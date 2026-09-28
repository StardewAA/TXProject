# 文件访问安全修复（③）验收记录 — 2026-09-24

> 范围：修复匿名上传漏洞 + 对私有文件的下载/预览落实登录与业务归属校验，同时保证已确认公开的图片仍可正常访问。
> 结果：**回归 48/48 全部通过**；公开面真实资产 **72/72 匿名可访问**；数据库回到基线。

> **后续**：本记录的方案在复评中暴露 5 项缺陷（冒领 / 校验顺序 / 历史回填口径 / `bindFiles` 覆盖 / 未接通真实业务），
> 整改与验收见 **`docs/file-access-control-review2-20260924.md`**（回归 23/23）。
> 其中已变更的口径：读取锚定「最早记录」、公开口径改为「真实业务记录引用」、
> `FileAccessTypeEnum/FileBindStatusEnum/FileBizTypeEnum` 已从 `infra-server` 收敛到 `infra-api`。
> 本文件中 §2 访问控制模型的判定顺序与 §4/§7 的遗留项，以 review2 文档为准。

---

## 1. 修复的问题

| # | 问题 | 修复前 | 修复后 |
|---|------|--------|--------|
| 1 | **匿名上传漏洞** | 仅带 `tenant-id` 请求头即可 `POST /app-api/infra/file/upload` 上传，无需任何登录态 | 上传/创建/预签名入口全部要求有效登录态；`tenant-id` 头不再充当身份 |
| 2 | **文件下载/预览零鉴权** | `/admin-api/infra/file/{configId}/get|preview/**` 对任意人可读任意路径（含私有凭证） | 默认私有：公开资源匿名可读；私有资源需登录 + 归属/参与方校验；无法确认归属即拒绝 |
| 3 | **归属不可追溯** | `infra_file` 无租户/上传者/业务绑定信息 | 新增 `tenant_id / owner_* / access_type / bind_status / biz_type / biz_id / bind_participants / bind_expire_time` |
| 4 | **会员无法读取私有文件** | 无会员侧读取通道（会员 token 走 admin-api 会被框架层拒绝） | 新增 `/app-api/infra/file/{configId}/get|preview/**`，与后台通道共用同一套鉴权逻辑 |
| 5 | **历史公开图被误判为私有** | —— | 全量核实后补录白名单（见 §5） |

---

## 2. 访问控制模型

**默认姿态：未命中公开白名单的文件一律私有；读取必须验证登录身份与业务归属。**

```
上传/登记  →  access_type=PRIVATE(2), bind_status=UNBOUND(0), 记录 owner + tenant + biz_type
              bind_expire_time = now + 60min  （本人短窗口可读）
业务提交  →  服务端 FileApi.bindFile(...) 核验并绑定（可带参与方）
              私有 → BOUND(1) + bind_participants
              公开 → 仅当 bizType.isPublicCapable() 才允许 PUBLIC(2)，否则强制私有
超期未绑定 →  cleanExpiredOrphanFiles() 清理（记录 + 存储对象）
读取判定  →  checkReadAccess(configId, path)：
              公开(access_type=1 或 bind_status=2)      → 匿名 ALLOW
              无记录                                    → 未登录 401 / 已登录 403
              私有：未登录                              → 401
              私有：后台文件管理权限(infra:file:query)   → ALLOW（平台运维）
              私有：跨租户                              → 403
              私有：上传者本人（UNBOUND 需未超期）        → ALLOW
              私有：已绑定单据参与方                     → ALLOW
              其余                                      → 403
```

**三层收口**（缺一不可）：
1. URL 层：`SecurityConfiguration` 放行读取路径（公开需匿名可读）→ 放行 ≠ 授权；
2. 控制器层：`FileController.hiddenReadWrite` / `AppFileController` 统一委托 `FileService.handleReadRequest`；
3. 服务层：`checkReadAccess` 做真实归属判定（唯一权威）。

> 公开白名单依据**服务端确认的业务类型 + 文件记录**建立，**不依赖**客户端传入的 `directory` 或路径前缀。

---

## 3. 关键设计决策：为什么新增 `/app-api` 读取通道

框架按 URL 前缀强制校验 `userType`（`TxgyWebAutoConfiguration#configurePathMatch` + `TokenAuthenticationFilter`）：

- `/admin-api/**` → 仅 **ADMIN**（member token 会被拒绝，实测返回 500）
- `/app-api/**` → 仅 **MEMBER**

历史现状：读取端点只挂在后台控制器（`/admin-api/infra/file/{configId}/get/**`），**会员根本没有可用的读取通道**。
为满足「PC/H5 用带 Token 的请求取文件」（方案 3），新增会员侧读取通道 `/app-api/infra/file/{configId}/get|preview/**`：

- 与后台通道**共用** `FileService.handleReadRequest`，杜绝两处实现产生鉴权差异；
- 公开资源在两个前缀下都允许匿名读取（历史 `admin-api` URL 不变，不受影响）；
- 私有资源在两个前缀下都需要登录 + 归属校验。

**前端约定**：会员上下文读取私有文件使用 `/app-api/infra/file/{configId}/get|preview/{path}` 并携带 `Authorization`；
与既有存储 URL 的差异仅在前缀（`/admin-api/` ⇄ `/app-api/`），同一 `configId + path`。

---

## 4. 变更清单

**服务端（`txgy-module-infra`）**
- `framework/file/core/enums/`：新增 `FileAccessTypeEnum / FileBindStatusEnum / FileBizTypeEnum / FileReadAccessEnum`
- `dal/dataobject/file/FileDO.java`：新增访问控制字段（保留 `@TenantIgnore`）
- `dal/mysql/file/FileMapper.java`：新增 `selectListByUrls` / `selectExpiredUnboundList`
- `service/file/FileService(+Impl)`：`checkReadAccess`、`handleReadRequest`、`bindFiles`、`cleanExpiredOrphanFiles`
- `controller/admin/file/FileController`：读取委托 `handleReadRequest`；新增 `POST /clean-expired-orphan`
- `controller/app/file/AppFileController`：**新增** `/{configId}/get/**`、`/{configId}/preview/**`
- `framework/security/config/SecurityConfiguration`：读取路径 permitAll（admin-api + app-api）
- `api/file/FileApi(+Impl)` + `api/file/dto/FileBindReqDTO`：服务间绑定接口（`/rpc-api`，不经网关暴露）

**数据迁移**
- `deploy/mall/sql/006-file-access-control.sql`：加列 + 索引 + 历史回填（公开 208 / 私有 33）
- `deploy/mall/sql/007-legacy-public-image-whitelist.sql`：**新增**，历史公开图核实后补录（1 条，见 §5）

**验证**
- `script/verify-file-access-control.sh`：回归脚本（48 用例，A~Z）
- `script/scan-public-file-refs.py`：扫描业务表引用的文件，核对「记录/公开性/内容」三条状态

**部署**
- 镜像 `deploy-infra:appapi-20260924`（= `deploy-infra:latest`）
- 回滚点：`deploy-infra:pre-appapi-20260924`（本次前）、`deploy-infra:fileaccess-20260924`、`deploy-infra:rollback-20260924`

---

## 5. 历史数据核实（"先核实再补入白名单"）

用 `script/scan-public-file-refs.py` 扫描商城对外展示列（商品/规格/品牌/分类/店铺/评论/秒杀/装修页/配送 logo/会员等级/订单快照等，含 JSON 数组字段），去重后 **123 个 `/infra/file/` URL**：

| 状态 | 数量 | 结论 |
|------|------|------|
| 公开(1/2) 且内容存在 | **72** | 正常，匿名 200 |
| 公开(1/2) 但内容已不存在 | 4 | **本次变更前即为 404**（内容早已删），非本次回归 |
| 无记录 且内容已不存在 | 38 | 同上，历史即坏 |
| **私有(2/0) 但内容存在且被公开面引用** | **1** | ⚠️ 真实回归 → 已补录 |
| 私有(2/0) 且内容不存在 | 1 | 历史即坏 |

**唯一真实回归**：`trade_delivery_express.logo` 指向的 `20260922/PixPin_2026-09-22_23-05-33.png`（下单页快递公司 logo，内容仍在，却被判为私有 → 401）。
已通过 `007-legacy-public-image-whitelist.sql` 按**精确 URL** 置为 `access_type=1 / bind_status=2 / biz_type=DELIVERY_LOGO`，验证匿名 GET 恢复 200。

> 复查脚本已纳入回归：B6「公开面引用完整性：无待处置项」。

---

## 6. 回归结果（48/48 全通过）

命令：`bash script/verify-file-access-control.sh`

| 组 | 覆盖点 | 结果 |
|----|--------|------|
| A | 匿名上传入口：无 token / 仅 `tenant-id` / create / 预签名 / admin-api 上传 → 全 401 | 5/5 |
| B | 公开图匿名可读：admin-api + app-api 的 get/preview + OSS 直连 + 公开面完整性 | 6/6 |
| C | 私有文件匿名：无 token / 仅 `tenant-id` / app-api / 不存在路径 → 全 401 | 4/4 |
| D | 登录上传落库字段：tenant / owner / 私有 / 待绑定 / 业务类型 / 过期窗口 | 2/2 |
| E | 本人 200、他人同租户 403、跨租户 403、匿名 401、admin 200、preview、会员走 admin-api 不放行 | 9/9 |
| F | 服务端绑定：参与方 200、非参与方 403、匿名 401、确认公开后匿名 200（双前缀） | 5/5 |
| G | 非法提权：私有业务类型声明公开被强制私有 + 落库复核 | 3/3 |
| H | 过期孤儿清理：守卫 → 置过期 → 清理 → 记录软删 → 无存活记录 | 5/5 |
| I | 后台接口鉴权未回退：page / delete / clean / upload / get → 全 401 | 5/5 |
| Z | 测试产物清理 + 库状态自检（无残留、无孤儿） | 3/3 |

**真实数据核验（独立于构造样本）**：public-facing 且内容存在且已确认公开的 **72 个 URL 匿名访问全部 200（0 个非 200）**。

**数据库基线**：`infra_file` 243 行（存活 241 / 软删 2）、`infra_file_content` 241 行、公开 209 / 私有 32（较回填前 208/33 变化 = 白名单补录 1 条）。

---

## 7. 遗留与后续（本次未做，需单独确认）

1. **业务模块绑定**：售后 / 询价等**提交接口**需调用 `FileApi.bindFile`（带真实参与方），前端使用返回的 URL。
2. **PC / H5 / Android WebView**：私有图改为「带 Token 请求 → `blob:` 渲染 → 页面销毁释放」；分别在三个端验收显示、过期登录、跨账号、跨租户拒绝。
3. **前端上传目录规范化**：收敛到服务端白名单目录（`mall/aftersale`、`mall/product` 等），去掉"自由 directory"。
4. **① 公开图按服务端白名单走 OSS**（用户已确认"验收后再做"）：公开资源走 OSS，私有文件暂留原存储。
5. **框架既有行为（未改动）**：会员 token 请求 `/admin-api/**` 会被 `TokenAuthenticationFilter` 判定"错误的用户类型"并返回 500；本次未改框架，前端统一走 `/app-api` 规避。若需返回 401/403 而非 500，建议另行评估。

---

## 8. 复现

```bash
# 回归（48 用例）
bash script/verify-file-access-control.sh

# 公开面引用完整性扫描（只读）
python script/scan-public-file-refs.py
```
