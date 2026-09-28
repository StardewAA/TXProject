# 文件访问控制整改（第六轮）验收记录 — 2026-09-24

> 前序：③ 安全修复 48/48 → review2 25/25 → review3 48/48 → review4 53/53 →
> **review5 64/64**（H5 阻塞根因、售后 + 店铺负责人、OSS 前提验证）。
>
> 本轮针对复评五项逐条整改。**结论：回归 `deploy/mall/verify-file-access.py` 77/77 PASS。**
> **全局 master 仍为 ID 4。**

---

## 1. OSS 分流：已实现（≠ 切 master，≠ 迁历史）

### 之前的问题
上传代码三处都用 `fileConfigService.getMasterFileClient()`，因此「OSS 能上传一张测试图」
并不等于商城已接入 OSS。

### 现在的实现
`FileServiceImpl` 新增 `resolveUploadClient(directory)`：

| 判定 | 结果 |
|---|---|
| `publicOssConfigId <= 0` 或就是 master | master（行为不变） |
| 目录命中 `FileBizTypeEnum.getByDirectory(...).isPublicCapable()` | **公开 OSS 配置（36）** |
| 其余目录（售后凭证 / 询价凭证等私有口径） | **master（4）** |
| OSS 配置缺失或异常 | 回退 master 并 warn |

配置：`txgy.file.public-oss-config-id: 36`（0 = 关闭分流）。

配套改动：
- `presignPutUrl` 同样按目录分流 —— 保证「签发预签名的 client」与「内容落地的 client」是同一个，
  上传凭证也按该 `configId` 签发。
- `presignGetUrl(url)` 不再想当然用 master，改为从 URL 里的 `/infra/file/{configId}/` 解析 client
  （OSS 直连域名解析不出 configId 时回退 master，这类地址本就可直接访问）。

读取侧**无需改动**：下载 URL 携带 `configId`，读取路径本就按 configId 解析 client。

### 证据（R 系列）
| 用例 | 断言 | 结果 |
|---|---|---|
| R1 | `mall/shop-logo`（SHOP_LOGO，公开口径）上传 → OSS 直连域名 | PASS |
| R1c | 记录落在 **config_id=36** | PASS |
| R1d | 判定来自服务端口径（URL 不含 `/infra/file/` 网关段，与客户端传参无关） | PASS |
| **R2** | `mall/aftersale`（私有口径）上传 → **仍走 master，config_id=4** | PASS |
| **R2b** | **全局 master 仍为 ID 4** | PASS |
| R3 | 分流**不改变访问控制**：上传后仍是 `私有(2)/待绑定(0)` | PASS |
| R3b | OSS 上的公开口径资源直连可读（200，图片字节） | PASS |
| R4 | 私有目录文件匿名读取 → **401**（未被分流带偏） | PASS |

### 已记录的安全取舍
`enablePublicAccess=true` 的对象存储无法按登录态拒绝读取。因此分流到 OSS 的文件在
「待绑定」窗口内，持有完整 URL 者即可读取（路径含日期 + 时间戳 + 随机数，不可枚举）。
这些目录的业务语义本就是「确认后即公开」；私有目录不受影响。

### 仍未做
- 应用侧「历史公开图片复制到 OSS」的迁移器；
- 把存量记录的 `config_id` 批量改到 36（等于事实切换，需你确认）；
- master 切换到 36（你要求暂不切换）。

---

## 2. 可复现部署：修掉两个明确问题 + 降低隐式依赖

| 问题 | 处置 |
|---|---|
| **体检 `grep` 让脚本自杀**：`grep -rl … \| wc -l` 在「没有匹配」时返回 1，`set -euo pipefail` 下直接退出 —— 而这恰是体检通过的情况 | 封装 `count_polluted() { … \|\| true; }`。已隔离复测：旧写法确实中断（`reached=` 未打印），新写法有匹配/无匹配都存活 |
| **009 引用了初始化脚本里不存在的表**：`ai_chat_role`、`txgy_demo01_contact` 在 001/002 里都没建表，全新库执行会报 1146 | 改为临时存储过程按 `information_schema.tables` 判断，表存在才计入公开面。**已在现库验证**：009 执行干净，公开 189 / 私有 52 不变 |
| 隐式依赖本机定制基础镜像 | `build-all.sh` 增加**基础镜像预检**，缺失即明确报错并指引「先按 compose 的 build: 段构建」，而不是中途失败 |
| 隐式依赖 Maven 离线缓存 | 新增 `MVN_OFFLINE=0` 开关（默认 1 即 `-o`），干净机器可在线拉依赖 |

> **如实说明**：`build-all.sh` 仍依赖 7 个历史基础镜像（它们是各轮迭代的累积产物），
> 本脚本只负责替换 jar / 前端产物。**真正从零部署仍需先按 `docker-compose.yml` 的 `build:` 段
> 构建服务镜像**，或向维护者索取基础镜像构建说明 —— 这一点尚未整合，也未在干净环境整体验证。

---

## 3. Android 验收方式：已改正（上一版写错了）

上一版写的 `pnpm build:app-android` **不存在**，项目也不是 uni-app 原生包。

实际情况（`txgy-mall-android/README.md` + `MainActivity`）：
- 包名 `cn.txgy.mall`，是**现有移动 H5 的原生 WebView 容器**（`new WebView(this)` + `loadUrl(target.entryUrl)`）；
- **商城地址安装后由使用者选择**（输入框 hint：`http://电脑局域网IP:3000`）；
- uni-app 侧只有 `pnpm build:h5`；构建走 `prepare-android-signing.ps1` → `build.ps1 -SdkRoot …`；
- 完整说明见 `docs/mall-android-signing-20260924.md`。

**结论：仅更新 H5 页面通常不需要重新打 APK** —— 改完 H5 重新部署 `mall-h5` 后，
App 里刷新即可；只有容器原生代码改动才需重新打包签名。文档 §6.2 已按此重写。

---

## 4. 店铺客服授权：从「仅负责人」扩展到「真实授权账号」

### 上一版的问题
只把 `product_shop.manager_user_id` 加入参与方 —— 但**店铺负责人 ≠ 所有合法客服**，
只用负责人验证就宣布"客服接通"是不成立的。

### 机制调查
库里存在真实的店铺授权关系：`product_shop_admin_authorization`
（`admin_user_id` + `shop_id` + `status`），以及每租户的 `mall_shop_kefu` 客服角色。

### 现在的实现
- 新增 `ProductShopApi.getAuthorizedAdminUserIds(shopId)` → **负责人 + 授权表中该店的全部账号**；
- `AfterSaleServiceImpl` 把它整体写入 `participants`（异常时回退仅负责人，不让客服全掉线）。

### 证据（S 系列）：四路对照，全部**真实账号、均无 `infra:file:query`**
| 用例 | 主体 | 与店铺的关系 | 结果 |
|---|---|---|---|
| Q4 | 80334 `kedun_songkai_1` | 店铺负责人（本轮夹具） | **200** |
| **S2** | **840001 `kedun_shop`** | **授权表中有该店记录**（真实数据，无夹具） | **200** |
| Q5 | 80335 `kedun_zhangwei_2` | 同角色、非负责人 | **403** |
| **S3** | **840011 `kedun_kefu01`** | **持客服角色，但无该店授权记录** | **403** |

`participants = 1:940001,2:80334,2:840001`；
`S4` 校验对照前提成立（840001 授权行 = 1，840011 授权行 = 0）。

> 说明：**客服角色本身不自动带出店铺授权**——必须以授权记录为准，这正是 S3 的价值。

---

## 5. 临时切换 master：如实记录与核对

**事实：上一轮我为了验证"能否写到 36"，曾把 `infra_file_config.master` 临时切成 36，
等待 75 秒后再改回 4。缓存没有回退，随后回归脚本开始拿到 OSS 直连 URL 并崩溃。**
因此不能写"master 从未动"。已 `restart infra` 让缓存按库重建，并复核 master=4。

### 该时间段写入 OSS 的对象核对结果
DB 侧 `config_id=36` 只有 2 条记录（id 2535/2536，时间 **17:03/17:04**，
对象 `public/5231dfd7….jpg`、`codex-smoke/20260924/oss-smoke.png`），
**两条都已软删、对象匿名 GET 均为 404** —— 早于我切换 master，不是我产生的。

我这一轮在 OSS 上实际留下的对象：

| 完整 key | 来源 | 当前状态 |
|---|---|---|
| `public/3b5142fb691344fabbe342cd59fca333.jpg` | `GET /admin-api/infra/file-config/test?id=36` | 200，**待清理** |
| `fileacl/20260924/179026145471170252/acl-01826f0f74.png` | 缓存滞留期间回归脚本的上传（67B 测试 PNG） | 200，**待清理** |
| `member/avatar/20260924/179026500514189398/acl-c955132c11.png` 等 | 回归（OSS 分流生效后公开目录的测试图） | 200，**待清理** |

确认：**只有测试图**，没有任何业务图片被误写到 OSS。
**清理方式**：本环境凭据无 DELETE 权限（签名删除返回 **403**），需手动删除；
回归脚本现在会在结束时打印本轮产生的 OSS key，便于逐条清理。

### 后续约束（已写入技能与脚本注释）
**不要再切全局 master 测试 OSS**。验证某个存储配置是否可用，统一用
`GET /admin-api/infra/file-config/test?id=<configId>`（返回该配置写入后的 URL，无需动 master）。

---

## 6. 回归与终态

**77/77 PASS**（C 14 + M 11 + N 6 + P 5 + Q 12 + **S 5** + **R 8** 等）。

终态基线：`infra_file` 存活 **241**（公开 189 / 私有 52）；
`master` **4=1 / 36=0**；店铺负责人 `NULL`、订单项 `after_sale_status=0`、售后单残留 0；
会员口令与企业采购成员夹具均已还原。

## 7. 上线清单

| 组件 | tag | 本轮变化 |
|---|---|---|
| **infra** | `deploy-infra:file-acl-20260924-4` | **OSS 分流（上传 client 按公开口径选择）** |
| **trade** | `mall-trade:file-acl-20260924-4` | **参与方扩展为店铺授权账号全集** |
| **product** | `mall-product:file-acl-20260924-3` | **新增 `getAuthorizedAdminUserIds`** |
| member | `mall-member:file-acl-20260924-2` | 无变化 |
| H5 / PC / admin | `-4` / `-1` / `-2` | 无变化 |

## 8. 仍未完成（单列，不计入"通过"）

1. **从零部署未整体验证**：`build-all.sh` 仍依赖历史基础镜像；干净环境初始化（001→002→005→006→007→008→009）
   已在 009 单点验证，但**整条链路未在空库上跑过**。
2. **H5 交互级上传**（点头像「修改」→ 选文件 → 回显/预览）仍无头环境驱动不了。
3. **Android 真机**未验（现为 WebView 加载 H5，见 §3）。
4. `FILE_BIND_BIZ_CONFLICT` 无黑盒用例（`/rpc-api` 不经网关）。
5. 历史公开图片迁移到 OSS 未做；master 未切换。
6. OSS 测试对象待手动清理（见 §5 清单）。
