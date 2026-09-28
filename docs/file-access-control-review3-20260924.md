# 文件访问控制 · 复评整改验收记录（第三轮） — 2026-09-24

> 前序：`docs/file-access-control-20260924.md`（③ 安全修复）、`docs/file-access-control-review2-20260924.md`（复评第一轮）。
>
> **后续**：第二次复评指出两处未闭合边界——「私有图缓存只按 URL 作键，切账号会串号」与
> 「历史无归属文件仍可被普通业务"声明式认领"并公开」。修复与验收见
> **`docs/file-access-control-review4-20260924.md`**（回归 53/53；本文件 §1③ 的缓存实现已被其取代）。
>
> **结论：回归 `deploy/mall/verify-file-access.py` 48/48 PASS，其中包含真实会员账号（非后台代替）的
> 上传 / 读取隔离 / 冒领 / 头像提权 / 询价提交与绑定回滚全链路。公开面 189/189 匿名 200 不回退。
> 数据库基线不变，测试夹具（含临时企业采购成员）全部还原。**

---

## 1. 本轮复评五点 → 处置

### ① 文件仍可能被他人引用后公开（上传者不一致只记日志、头像绑定未传上传者）

**问题链**：`bindFiles` 在「上传者与请求方不一致」时**只 log 不拒绝**；而 `MEMBER_AVATAR / PRODUCT_IMAGE /
SHOP_LOGO / COMMENT_IMAGE` 等业务类型的 `publicCapable=true`。于是**同租户任意登录用户**只要把
「他人尚未绑定的私有图片地址」当作自己的头像 / 商品图 / 评价图提交，就会被服务端**抬成匿名可读**。
另外 `ProductFileBindingService` 与 `MemberUserServiceImpl.bindMemberAvatar` 调用时**根本没传上传者身份**。

**修复（`FileServiceImpl.bindFiles` 3.3 / 3.4）**：

```
3.3 上传者核验（必须在放行前拒绝，不能只记日志）
    a) 文件已有明确归属 → (ownerUserType, ownerUserId) 必须完全一致，否则 FILE_BIND_OWNER_MISMATCH
    b) 文件无归属（历史/异常数据）→ 请求必须显式声明归属才允许补录；否则同样拒绝
3.4 公开绑定二次确认：只有归属已核验一致的请求才能把文件抬为公开
```

同时补齐**所有调用点的上传者声明**：

| 调用点 | 归属声明 |
|--------|----------|
| `ProductFileBindingService`（商品图 / SKU / 店铺 logo / 评价图） | 取自 `SecurityFrameworkUtils.getLoginUser()`（上传者=当前登录身份），为空则不声明 → 由 infra 拒绝 |
| `MemberUserServiceImpl.bindMemberAvatar` | `(MEMBER, userId)`（头像由会员本人上传） |
| `AfterSaleServiceImpl.bindAfterSaleImages` | `(MEMBER, afterSale.getUserId())`（已有） |
| `PurchaseInquiryService.bindInquiryImages` | `(MEMBER, memberId)`（已有） |

**证据**：
- `M8` 会员 A 把自己的私有图设为**会员 B** 的头像 → `1001003014 FILE_BIND_OWNER_MISMATCH` 拒绝；
- `M8b` 该图 `access_type` 仍为 2（**未被抬公开**）；
- `M8c` 会员 B 的资料整体回滚（`avatar` 未被写入）——证明拒绝发生在事务内且生效；
- `M9/M9b` 对照：会员 A 用自己的图设头像 → 成功且正确抬为公开（不是"一刀切全拒"）；
- `N4/N4b/N4c` 询价路径引用他人私有图 → 报错 + **询价单整体回滚** + 被拒文件未被改写。

### ② 后台权限放得过宽（同租户任意后台账号都能读已绑定私有文件）

**问题**：`checkReadAccess` 第 7 步对 `bind_status=BOUND + 同租户 ADMIN` **无条件放行**，等于租户内
无差别放行，绕过了店铺 / 单据 / 客服的授权范围。

**修复**：**删除该兜底**。非参与方的后台用户必须**显式持有 `infra:file:query`**（第 7 步，即原第 8 步）
才能读取。业务侧若需要让特定后台角色（店铺客服 / 售后审核）读取，应把它作为 `participant` 传进
`bindFile`（第 6 步按 `类型:编号` 精确匹配），而不是依赖租户内全量放行。

最终判定顺序：

```
1 无文件记录 → 匿名 401 / 已登录 403
2 公开资源（access_type=1 或 bind_status=2） → 匿名 ALLOW
3 私有 + 未登录 → 401
4 私有 + 租户边界不一致 → 403        ← 先于任何特权
5 上传者本人（类型+编号严格相等；待绑定窗口内）→ ALLOW
6 已绑定单据的参与方（类型:编号 精确匹配）→ ALLOW
7 同租户 + 显式持有 infra:file:query → ALLOW
8 其余 → 403
```

**证据**：`C7c` 同租户后台 `comency`（**无** `infra:file:query`）读他人已绑定私有文件 → **403**
（旧实现此处为 200）；`C7` 外租户后台（**有** `infra:file:query`）→ 403；`C6b` 同租户 + 有权限 → 200（运维通道保留）。

### ③ H5 私有图片仍直接用 URL（窗口期不等于登录凭证）

**修复**：新增 `sheep/helper/private-image.js`：

- `isFileServiceUrl(url)`：识别文件服务地址（`/infra/file/`）；
- `toMemberFileUrl(url)`：**前缀改写** `/admin-api/...` → `/app-api/...`（框架按 URL 前缀强校验 userType，
  会员必须走 `/app-api`；文件服务签发的 URL 无论谁上传都写成 `/admin-api`）；
- `loadPrivateImage(url)`：走 `uni.downloadFile` 并带上 **`Authorization` + `tenant-id`**（复用
  `tenant-runtime` 的会话快照与 `tenantContext.accountHeaders`），得到本地/blob 路径；
  带**引用计数缓存**（同一 URL 多处渲染只下载一次）、租户/身份切换时丢弃结果、失败不缓存；
- `releasePrivateImage(url)`：引用归零后回收（H5 下 `URL.revokeObjectURL`）。

接入 `sheep/components/s-uploader/upload-image.vue`：回显与 `uni.previewImage` 预览统一改用本地路径；
**未解析出来之前返回空串（占位）**，绝不把原始 URL 交给 `<image>`（否则既是 401 破图也是地址泄露）；
新增 `owned` 引用台账，处理「下载中重复触发」与「列表项被删」两种情况，避免重复计数与 blob 泄漏。

**产物 & 浏览器实测**：
- H5 dist 中 `s-uploader.CBYP4S7g.js` 含本模块代码（`私有图片读取失败`）与 `downloadFile`；
- **真实 Chromium 内同源实测**（`localhost:3000` 页面上下文，`fetch` 走 H5 自己的 `/app-api` 反向代理）：

| 请求 | 结果 |
|------|------|
| `'/app-api' + url.slice(url.indexOf('/infra/file/'))` + `Authorization: Bearer <会员token>` | **200**，`image/png`，`blob.size=67`，成功生成 `blob:http://...` |
| 同上，**不带** Authorization | **401** |
| **原始** `/admin-api/...` + 会员 token | **500**（框架按前缀校验 userType） |

  即：raw URL 无论用哪个前缀，塞进 `<image>` 都读不到；必须「改写前缀 + 带 token 取流 + blob 渲染」，
  与本模块实现一致（这也解释了为什么 H5 之前必然破图）。

### ④ 25/25 PASS 不能作为完整验收结论（会员登录未测通；C15 无条件通过）

**问题**：脚本 `C15` 是 `record(..., True, ...)`——**登录失败也记 PASS**；且会员归属通道用后台账号
（`comency`）代替，属于"等价覆盖"而非真实会员验证。

**修复**：

1. **C15 删除**，改为真实会员用例 `M0`：`member_login()` 失败即 `FAIL`（并给出原因），不再存在无条件通过；
2. **查清会员登录失败的真实原因**（此前结论有误）：
   - 不是"缺 `member_platform_account` 夹具"，而是 `MallTenantCatalogService` 的
     **`mall.tenant.entries` 白名单里没有租户 1**（只配了 2001-2024 与 3000，且须 `publicVisible` +
     `system_tenant` 有效）。`policy.resolve(1, false)` 因此抛 `MALL_TENANT_UNAVAILABLE(1004003009)`。
   - 改用白名单内可用租户即可：
     `tenant 2001` → `13920010001/2/3`、`tenant 2002` → `13920020001/2/3`，密码 `admin123`。
3. **新增真实会员用例** `M1-M9`：上传落库归属、本人 200 / 同租户他人 403 / 跨租户 403 / 匿名 401、
   会员 token 不能混用 `admin-api`、冒领 `OWNER_CONFLICT`、头像提权拒绝与对照成功。
4. **新增询价端到端** `N0-N4c`：真实提交 → 服务端 `bindFile` → `INQUIRY_IMAGE + 私有 BOUND + 参与方` →
   本人可读 / 他人 403；以及**绑定失败整体回滚**的验证。
   询价要求「企业采购成员」（`member_identity=buyer` + 已审核企业成员关系 + 企业 `audit_status=1`），
   演示库中唯一满足的是 tenant 2009 的 `member 300040122`（无手机号无法登录）→ 用例内使用
   **可回滚夹具**（临时借用同库已知口令哈希 + 临时手机号），用例结束**原值还原**。

### ⑤ 询价保存没有检查绑定接口的业务错误

**修复**：`PurchaseInquiryService.bindInquiryImages` 的 `fileApi.bindFile(bindReq)` 改为
`fileApi.bindFile(bindReq).getCheckedData()`，与 `save()` 上的 `@Transactional` 及注释
「绑定失败即回滚」一致。同时把 `AfterSaleServiceImpl` 中已过时的注释（"本租户后台账号可读"）更正。

**证据**：`N4` 引用他人私有图提交询价 → 绑定报错上抛；`N4b` `product_purchase_inquiry` 中
`request_key` 无残留（**整体回滚**）；`N4c` 被拒文件未被改写。

---

## 2. 回归结果（48/48 PASS）

命令：

```bash
python deploy/mall/verify-file-access.py     # 需宿主机可用 docker + 48080 网关
```

真实身份（全部真实登录，非构造 token）：

| 身份 | 说明 |
|------|------|
| `admin` (id 1, tenant 1, super_admin) | 有 `infra:file:query` |
| `comency` (id 300030204, tenant 1, `mall_shop_merchant`) | **无** `infra:file:query` —— 权限收窄的负例主体 |
| `xibeihengtai` (id 80000, tenant 2001, `tenant_admin`) | 跨租户后台，有 `infra:file:query` |
| 会员 `200140101 / 200140102` (tenant 2001) | 本人 / 同租户他人 |
| 会员 `200240101` (tenant 2002) | 跨租户会员 |
| 会员 `300040122` (tenant 2009) | 企业采购成员（临时夹具） |

| 组 | 用例 | 覆盖 |
|----|------|------|
| C0-C1 | 4 | 存储配置口径（ID 4 master / ID 36 非 master）、负例主体无权限、**公开面 189/189 匿名 200 不回退** |
| C2-C4c | 7 | 上传落「私有+待绑定」、归属取自登录上下文、匿名 401、**>1h 仍可读**、超期草稿对匿名 401 而对持 `file:query` 后台 200 |
| C5-C7c | 7 | 同 ID 跨身份 403、历史 `owner_user_type=NULL` 403、**跨租户持 `file:query` 403**、**同租户无 `file:query` 读已绑定私有 403** ★② |
| C8-C10 | 5 | 匿名 `/file/create` 拒绝、无凭证 `NO_TICKET`、**他人 path 登记 `OWNER_CONFLICT`** ★①、原记录归属未被遮蔽、本人幂等 |
| C11-C14 | 4 | 孤儿清理正例 / **业务引用护栏（不误删）** / 历史行免疫 / **公开口径无「无引用却公开」** ★③ |
| M0-M9b | 11 | **真实会员通道**：登录、上传归属、本人 200、同租户他人 403、跨租户 403、匿名 401、通道不混用、冒领拒绝、**头像提权拒绝 + 未被抬公开 + 资料回滚** ★① |
| N0-N4c | 12 | **询价端到端**：提交落库 → `INQUIRY_IMAGE/私有/BOUND/参与方` → 本人 200 / 他人 403；**绑定失败整体回滚 + 文件未被改写** ★⑤ |

**数据库基线**：`infra_file` 存活 241（公开 189 / 私有 52）；`deploy/mall/verify-file-access.py` 自带清理，
`fileacl/` 测试残留 0、`product_brand` 夹具 0、询价夹具 0、**会员手机号/口令已还原**。

---

## 3. 部署

```bash
# 后端（本机 Reactor 构建 → 补丁镜像仅替换 app.jar）
mvn -o -B -DskipTests -pl txgy-module-infra/txgy-module-infra-api,\
txgy-module-infra/txgy-module-infra-server,txgy-module-mall/txgy-module-product-server,\
txgy-module-mall/txgy-module-trade-server,txgy-module-member/txgy-module-member-server -am package

# 前端（本机构建 → 补丁镜像仅替换静态产物）
cd txgy-mall-uniapp-master && SHOPRO_BASE_URL="" SHOPRO_API_PATH=/app-api SHOPRO_STATIC_URL="" \
  SHOPRO_TENANT_ID=1 SHOPRO_H5_URL="" SHOPRO_WEBSOCKET_PATH=/infra/ws pnpm build:h5

# 上线
cd deploy && docker compose -p deploy \
  -f docker-compose.yml -f docker-compose.mall-rollout.yml \
  -f docker-compose.mall-android-rollout.yml \
  -f docker-compose.file-acl-rollout.yml -f docker-compose.file-acl-frontend.yml \
  up -d --no-build infra trade product member mall-h5
```

| 服务 | 本轮上线镜像 | 上一轮（回滚点） |
|------|--------------|------------------|
| infra / trade / product / member | `*:file-acl-20260924-2` | `*:file-acl-20260924-1` |
| H5 | `txgy-mall-h5:file-acl-20260924-2` | `txgy-mall-h5:file-acl-20260924-1` |
| PC / admin | 未改动（保持 `-1`） | — |

---

## 4. 遗留 / 未覆盖（明确列出，不计入"通过"）

1. **Android App（uni-app 原生端）内的上传 / 回显 / 预览未实测**：本轮覆盖了 H5（产物级 + 真实 Chromium 内
   同源机制实测）与 API 层。移动端走同一份 `sheep/helper/private-image.js`（`uni.downloadFile` 在 App 端同样
   支持自定义 header），但**未在真机 / 模拟器上验证**，需在 Android 打包后单独验收。
2. **H5「组件交互级」未实测**：H5 的租户入口在本机是**平台门户**（`isPlatformRoute('/')` 为真，会剥离
   URL 里的 `tenantId` 提示），且平台账号登录依赖 `member_platform_account*` 夹具（当前库无数据），
   因此无法在浏览器里走完「登录 → 进个人资料 → 触发上传 → 观察回显」的 UI 链路。
   已用**同源 fetch 实测**替代性地证明了整条机制（见 §1③ 表格）。
   待环境具备租户入口会话后，请补跑：进入 `#/pages/index/user?tenantId=2001` → 登录
   `13920010001/admin123` → 个人资料 → 改头像 → 断言渲染元素 `src` 以 `blob:`/本地路径开头、且**不含** `/infra/file/`。
3. **④ 重绑冲突（`FILE_BIND_BIZ_CONFLICT`）仍无黑盒用例**：`FileApi` 走 `/rpc-api` 不经网关，
   无法用 HTTP 直接构造"已绑定 A 单据的文件再绑 B 单据"。仍为**代码走查 + 定义复核**，
   建议补 infra-server 集成测试直接调 `FileServiceImpl.bindFiles`。
4. **店铺 / 客服的精细授权范围**：本轮把"租户内任意后台账号可读"收窄为"须显式持有 `infra:file:query`"，
   更细的"仅本店铺客服可读本条售后凭证"需要业务侧在 `bindFile` 时把具体后台账号作为
   `participant` 传入（框架已支持，业务尚未接入）。
5. **售后（`AFTER_SALE_IMAGE`）未走真实提交链路**：售后需先有订单，本轮只对**询价**做了端到端
   （`N0-N4c`，含绑定成功与失败回滚）。售后与询价调用的是同一套 `bindFiles` + 同样的
   `getCheckedData()` 回滚约定，风险低，但**未实测**。
6. **OSS 分流（①）继续暂停**：ID 4 仍为 master、ID 36 仍为非 master；按约定待上述项验收后再做。
