# 文件访问控制整改 — 最终交付说明（2026-09-25）

> **接手更新（2026-09-25）：**本文的镜像版本与测试数量是上一轮联调快照，不代表新机验收。
> 新机器唯一启动入口改为 `deploy/mall/start.ps1` / `start.sh`，见 [独立部署说明](../deploy/mall/README.md)。
> 真实 OSS 和商品页面验收的最新结果见 [OSS 联网交接](OSS联网部分交接记录-20260925.md)。
> 下文 7 个基础/补丁镜像只用于已有联调栈，不能替代网关、支付等完整依赖的源码构建。

> 范围：txgy-cloud 文件访问控制复评整改（round 7→8）收尾，补齐两项交付缺口：①不依赖本机历史镜像的从源码部署流程；②已确认公开但 OSS 发布失败文件的可追踪、可安全重试补传机制。
> 配套文档：review6（2026-09-24）记录前序修复；本文只记"本轮交付、待验收、四分类测试、启动说明、手工操作"。

---

## 0. 一句话结论

- **两项缺口已补齐并验证**：
  - 缺口①：`build-base.sh` 从源码重建全部 7 个基础镜像（后端 maven 容器内编译、admin 自带 nginx 配置），彻底消除对 `deploy-infra:latest` 等本机历史镜像的依赖。
  - 缺口②：`010-oss-promote-retry.sql` + 补传三列 + `/retry-oss-promotion` + `/public-unpromoted` + 定时 Job，并修复"再次保存就跳过"漏洞；**U6/U7 直接 GET OSS 对象 URL 并比对内容一致，证明文件真实进入公共桶**。
- **接口回归 95/95 PASS**（含新增 U1~U8 补传验收）；`master` 保持 **4**，私有文件不迁入公共桶。
- **四类"未实际验证"的交付项仍标为【待验收】**：①后台页面真实上传 ②Android 真机 ③从零部署 ④OSS 提升稳定性。
- **构建脚本与部署镜像版本已同步**：infra 升到 `file-acl-20260924-7`，与 rollout 覆盖层一致。

---

## 1. 已完成成果（代码 / 配置，保留）

| 项 | 内容 | 状态 |
|---|---|---|
| 上传落点 | 删除 `resolveUploadClient(directory)`，上传一律走 master（config_id=4），绝不由客户端目录决定公开存储 | ✅ |
| OSS 提升 | `promoteToPublicOss`：仅当**服务端绑定确认 PUBLIC** 后，把内容复制到 OSS(36) 并改写 `config_id/url`；失败则保留 master + warn（优雅降级） | ✅ |
| 后台真实上传 | `ProductFileBindingService`（商品封面/轮播图/店铺 Logo/评论图）接通，SPU/店铺保存触发 `bindFiles`；真实端点回归 T 系列全过 | ✅ 实现 |
| 路径兜底读取 | `FileMapper.selectFirstByPath` + `FileServiceImpl.resolveFileForRead` + 按文件真实 `config_id` 取内容，修复"提升后网关 URL 取回 404" | ✅ |
| 历史口径 | `009-file-public-recompute.sql` 按真实业务记录引用确认公开（动态判断表存在）；**已改为按 `path` 匹配**（原按 `url` 匹配，OSS 提升后 url 改写会导致重算误回落私有） | ✅ |
| OSS 补传机制（缺口②） | `010-oss-promote-retry.sql` 加 `promote_status/promote_fail_count/promote_last_error` 三列；`retryPublicOssPromotion`/`retryOssPromotion`/`listPublicUnpromoted` + `FileMapper.selectPublicUnpromoted`（强制 `access_type=1`，私有绝不触碰）；`bindFiles` 修复"已是 PUBLIC 却未提升就直接 continue 跳过"的漏洞 | ✅ |
| 补传入口 | 管理端点 `POST /infra/file/retry-oss-promotion`（fileId 单文件 / 缺省批量）+ `GET /infra/file/public-unpromoted`（运维清单）；定时 `OssPromoteRetryJob`（默认开，5 分钟） | ✅ |
| 从源码部署（缺口①） | `build-base.sh`（7 个基础镜像从源码重建）；`Dockerfile.admin-base` + `nginx-spa.conf`（admin 自带 80 端口 + SPA 回退，消除对历史 admin 基础镜像依赖） | ✅ |
| 其余前序修复 | 上传凭证、冒领/租户/归属校验、孤儿清理、会员/询价/售后/客服授权链路 | ✅ 保留 |

**安全结论**：round 6 的两个真实缺陷已修复——「客户端目录决定公开存储」已移除；「私有待绑定文件实际已公开」已修复（待绑定窗口内持网关私有地址、匿名 401，确认公开后才提升）。`master` 始终保持 **4**。

---

## 2. 【待验收】清单（需用户 / 稳定环境确认）

| ID | 待验收项 | 说明 |
|---|---|---|
| #37 | 后台页面真实上传商品封面/轮播图/店铺 Logo → 保存 → 重新打开 → 匿名访问 | 机制已由真实端点回归等价证明，但无浏览器真实点击 |
| #38 | Android 真机 WebView 加载移动 H5 验收 | APK 安装后输入局域网 IP:3000 加载 H5，验证图显示/上传回显 |
| #39 | 从零部署完整验收（build-all.sh 重建 + 全量 up + 回归） | 未在真正干净机器从零 build+up |
| #40 | 稳定网络下 OSS 提升复验 | 当前 OSS 端点 TLS 偶发中断导致提升回退（约 1/3） |

---

## 3. 四类测试记录（分别记录，互不替代）

### 3.1 接口测试（verify-file-access.py，真实 HTTP + 真实库）
- **用例**：95 例（C0~C14、M0~M9d、N0~N4c、P1/P1b/P2/P2b/P2c、Q0~Q5c、S0~S4、R1~R4、T1~T5、**U1~U8**）。
- **本轮结果**：**95/95 PASS**（2026-09-25，infra `-7`）。
  - 新增 U 系列覆盖缺口②：U1 上传落 master → U2 模拟 stranded（access_type=1/bind_status=2/config_id=4/promote_status=3）→ U3 运维清单可追踪 → U4 补传前网关匿名可读 → U5 补传后 config_id=36/url 改 OSS → **U6 直接 GET OSS 对象 URL 比对内容（=原 PNG）** → U7 按 bucket+path 直连再比对 → U8 清单不再含该文件（闭环）。
- **OSS 提升验收口径（关键）**：不看"图片返回 200"（网关会按 path 兜底，未进 OSS 也 200），而是 **U6/U7 直接访问 OSS 对象 URL 且 `body == 原 PNG 字节`**，证明文件真实进入公共桶。
- **结论**：接口契约稳定通过；补传机制"可追踪、可安全重试、私有绝不迁移、真实进入 OSS"由 U 系列直接验证。
- **与空库/页面/部署测试互不替代**：本测试验证"运行时接口契约"，不验证"脚本在空库能否干净跑通"也不验证"人机交互"。

### 3.2 空库 SQL 测试（001→010，隔离临时库，未动运行库）
- **过程**：建临时库 `acl_empty_test` → 导入 `001-schema / 002-demo / 006 / 007 / 008 / 009 / 010`（sed 替换 `USE ruoyi-vue-pro` → 临时库）→ 校验 → 丢弃临时库。
- **结果**：7 个文件**全程无报错**；`master` 确认 = **4**（id4 `master=1`、id36 `master=0`）；`010` 三列（promote_status/promote_fail_count/promote_last_error）均存在。
- **只验证**："脚本在空库可干净跑通 + master 保持 4 + 010 列就位"。不验证页面/真机/完整部署。

### 3.3 页面验收（【待验收】#37）
- **未做**：无浏览器真实点击。机制已由 3.1 的 T 系列（真实端点 `/admin-api/infra/file/upload` → `/admin-api/product/shop/update` → 重新打开 `/product/shop/get` → 匿名访问网关地址）证明等价正确。
- **需用户按 §6 步骤在 PC 后台逐一点击完成。**

### 3.4 完整部署验收（【待验收】#39）
- **未做**：未在真正干净机器从零 `build + up`。
- 现有联调栈已局部验证镜像/脚本一致性（见 §4），但"从零"需独立验收。

---

## 4. 构建脚本与部署镜像版本（已同步）

| 组件 | build-all.sh 产出 tag | rollout 覆盖层 tag | 当前运行 tag | 一致 |
|---|---|---|---|---|
| infra | `file-acl-20260924-7` | `file-acl-20260924-7` | `file-acl-20260924-7` | ✅ |
| trade | `file-acl-20260924-4` | `file-acl-20260924-4` | `file-acl-20260924-4` | ✅ |
| product | `file-acl-20260924-3` | `file-acl-20260924-3` | `file-acl-20260924-3` | ✅ |
| member | `file-acl-20260924-2` | `file-acl-20260924-2` | `file-acl-20260924-2` | ✅ |
| pc | `file-acl-20260924-1` | `file-acl-20260924-1` | `file-acl-20260924-1` | ✅ |
| h5 | `file-acl-20260924-4` | `file-acl-20260924-4` | `file-acl-20260924-4` | ✅ |
| admin | `file-acl-20260924-2` | `file-acl-20260924-2` | `file-acl-20260924-2` | ✅ |

> **本次修正**：`build-all.sh` 原后端产出 tag 滞后（`-3/-3/-2/-2`），与上线覆盖层（`-6/-4/-3/-2`）不一致；已同步为 `-7/-4/-3/-2`。前端 tag 三方本就一致。
> 注意：`build-all.sh` 仅替换 jar / 前端产物，**基础镜像（BASE_*）可经 `build-base.sh` 从源码重建**（缺口①），不再依赖本机历史镜像；脚本有预检，缺失即报错退出。

---

## 5. 统一启动说明

### 5.1 已有联调栈（推荐，最快）
```bash
cd deploy
docker compose -p deploy \
  -f docker-compose.yml \
  -f docker-compose.mall-rollout.yml \
  -f docker-compose.mall-android-rollout.yml \
  -f docker-compose.file-acl-rollout.yml \
  -f docker-compose.file-acl-frontend.yml \
  up -d --no-build infra trade product member mall-pc mall-h5 frontend
```
- 必须先确保基础镜像存在（见 `build-all.sh` 预检）。
- 健康检查：`deploy-infra-1` 等应 `healthy`。

### 5.2 从源码重建（缺口① + 补丁叠加）

以下仅为已有联调栈的补丁维护流程；在仓库根目录执行，不是新机器整套部署流程。
```bash
# 第一步：从源码重建基础镜像（不依赖本机历史镜像）
bash deploy/patch-image/build-base.sh            # 或 backend / frontend
# 第二步：补丁叠加（替换 jar / 前端产物到 file-acl-* 镜像）
bash deploy/patch-image/build-all.sh             # 或 backend / frontend
```
- 关键开关已内置：`MSYS_NO_PATHCONV=1`（防 H5/PC 产物路径污染）、补丁 Dockerfile 不写 `# syntax=`（防 Docker Hub 拉取）、product jar 路径差异。
- admin 由 `Dockerfile.admin-base`（自带 80 端口 + SPA 回退 nginx 配置）从源码打包，不再依赖历史 admin 基础镜像。
- 产出 tag 见 §4，须与 §5.1 覆盖层一致（已同步）。

### 5.3 初始化数据库（新库）
顺序：`001-schema` → `002-demo` → `004-public-origin.sh`（容器 entrypoint，设 config 域名）→ `005-ready` → `006` → `007` → `008` → `009` → `010` → `011-ready`（最终就绪标记）。
- `004` 是 mysql 容器 entrypoint 脚本；手动场景可执行其等效 `UPDATE`。
- `009` 已按 `path` 匹配（OSS 提升后重算不误回落私有）；`010` 增加补传追踪三列。
- 校验：`infra_file_config` id4 `master=1`、id36 `master=0`。

### 5.4 验证
- 接口回归：`python3 deploy/mall/verify-file-access.py`（宿主机，需能访问 docker + 网关 48080）。
- 公开面匿名可读：浏览器无痕访问公开图片 URL 应 200。

---

## 6. 需要我（用户）手工完成的操作（单独列出）

1. **后台页面上传验收（#37）**：登录 PC 后台 → 商品 → 新增/编辑 SPV(SPU) → 上传商品**封面图、轮播图** → 保存 → 重新打开该商品 → 确认图片显示 → 浏览器**无痕窗口**粘贴图片 URL 确认匿名 200。店铺 → 编辑店铺 → 上传 **Logo** 同理。
2. **Android 真机验收（#38）**：安装 `txgy-mall-android` APK → 输入电脑局域网 `IP:3000` → 打开 H5 → 验证私有图（带 token blob）/ 公开图（直连 OSS）显示、上传回显。仅更新 H5 通常不需重打 APK。
3. **从零部署验收（#39）**：按 `deploy/mall/README.md` 执行独立启动脚本，自动导入 001→011，再验收登录、同源接口和业务流程。不要叠加现有联调栈的 rollout 文件；历史接口用例数量不代表新机部署通过。
4. **OSS 提升稳定性复验（#40）**：在 OSS 网络稳定时复跑接口回归，确认 `M9c/T3b` 稳定 PASS（`config_id` 稳定变 36）。若仍偶发，属 OSS 端点 TLS 不稳定，需运维侧排查网络/TLS——代码已优雅降级，无需改。
5. **（安全提醒，非本次范围）** 正式部署前轮换 OSS AccessKey 并收紧 RAM 最小权限；当前凭证仅用于联调。

---

## 7. 已知限制 / 诚实声明

- **OSS 提升 = 尽力而为 + 可重试**：OSS TLS 偶发中断时文件留在 master 但仍经网关匿名可读；现已具备补传机制（端点 + 定时 Job + 三列追踪），stranded 文件可被追踪并补传，不再"永久沉在 master"（见 §1、U 系列）。
- **页面 / 真机 / 从零部署未做真实人机验证**：机制由接口回归等价证明，但最终交付须经 §6 手工验收。
- **历史图片迁移（存量文件搬 OSS）未做**，属独立阶段。
- **凭证纪律**：全程未读取/打印 `.env`/AccessKey/Secret，未关闭 TLS 校验、未降级 HTTP。
- **009 重算口径修正（本轮）**：原 `009` 以 `url` 为子串匹配公开面引用，OSS 提升后 `url` 被改写为 OSS 直连，重算会把已提升的公开文件误回落私有；本轮已改为按不变的 `path` 匹配，并同步修正了运行库中被误回落的数据（`config_id=36` 即已入公共桶，一律 `access_type=1`）。
