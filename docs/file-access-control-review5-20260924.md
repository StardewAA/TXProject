# 文件访问控制整改（第五轮）验收记录 — 2026-09-24

> 前序：`docs/file-access-control-20260924.md`（③ 安全修复 48/48）、
> `docs/file-access-control-review2-20260924.md`（复评一轮 25/25）、
> `docs/file-access-control-review3-20260924.md`（复评二轮 48/48）、
> `docs/file-access-control-review4-20260924.md`（复评三轮 53/53）。
>
> **本轮结论：回归 `deploy/mall/verify-file-access.py` 64/64 PASS；**
> H5 登录/数据加载阻塞已定位并修复；售后凭证 + 合法店铺客服授权已接通并验证；
> OSS 分流完成**前提验证**但未切换；**master 仍为 ID 4**。

---

## 1. H5 阻塞的根因与修复（本轮最重要发现）

### 现象
H5（`localhost:3000`）页面能出框架但**数据全空**、登录不可用，后台接口零请求。
此前两轮我都把它误判为「缺 `member_platform_account` 夹具」或「平台门户入口限制」——**都是错的**。

### 真实根因：Git Bash 的 MSYS 路径转换污染了构建期环境变量

`SHOPRO_API_PATH=/app-api` 这类**以 `/` 开头**的值，在 Git Bash 里会被自动做
POSIX 绝对路径 → Windows 路径的转换：

```
SHOPRO_API_PATH=/app-api  node -e "console.log(process.env.SHOPRO_API_PATH)"
  → C:/Users/ltr33/.workbuddy/binaries/PortableGit/versions/1.2.0/app-api
```

于是产物里 `baseUrl + apiPath` 被内联成
`C:/Users/.../PortableGit/versions/1.2.0/app-api`，**所有 `uni.request` 都发往 `file://`**，
相对地址基准异常、后端完全收不到请求 —— 这才是"登录/数据加载阻塞"的真正原因。

对照实验（决定性）：
| 场景 | 结果 |
|---|---|
| 页面内原生 `fetch('/app-api/...')` | **200**（浏览器基准正常） |
| 应用自身 `uni.request` | 落到 `file://`，后端**零请求** |
| 哨兵值 `SHOPRO_BASE_URL=http://SENTINEL.invalid` 构建 | 哨兵**确实进入产物** → 确认是构建期注入而非运行期 |

### 修复
1. `sheep/config/index.js`：空 `baseUrl` 时回落 `location.origin`（兜底，防再次被污染）。
2. **构建必须加 `MSYS_NO_PATHCONV=1`**（已固化到 `deploy/patch-image/build-all.sh`）。

### 验证（真实 Chromium）
- 重建后产物：`sk="/app-api"`，全量 grep `PortableGit|C:/Users` → **0 命中**。
- 首页：热门租户/商品全部 200，无"暂时无法加载"。
- 登录：`/pages/user/info?tenantId=2001` → 填 `13920010001 / admin123` + 勾选协议 →
  `account-login` **200**、token 落 localStorage、页面进入登录态。

### 连带发现：`admin` 前端此前也被同一个坑污染
上一轮我重建 admin 时没加该开关 → **8 个产物文件含盘符路径**。本轮用
`MSYS_NO_PATHCONV=1` 重建 `dist-facl2`，体检 **0 命中**、`/admin-api` 正确，已上线 `-2`。

---

## 2. 售后凭证 + 合法店铺客服授权（Q 系列，11 例）

### 改了什么
`AfterSaleServiceImpl.bindAfterSaleImages`：参与方从「仅买家本人」扩展为
**「买家本人 + 该店铺的后台负责人」**，负责人由 `product_shop.manager_user_id` 解析
（`resolveShopManagerUserId`，解析失败降级为仅买家、**不阻断提交**）。

设计要点：**刻意不用**「同租户任意后台账号可读」这种粗放放行 —— 店铺客服要读凭证，
必须由业务侧把具体账号作为 `participant` 传入。

### 对照实验（关键）
80334 与 80335 **同一角色 `kedun_60167`、同样不具备 `infra:file:query`**，
唯一差别是「是否为该店铺的后台负责人」：

| 用例 | 主体 | 期望 | 实际 |
|---|---|---|---|
| Q2c | 绑定后 `bind_participants` | 含 `2:80334` | `1:940001,2:80334` ✅ |
| Q3 | 买家本人 | 200 | 200 ✅ |
| **Q4** | **店铺负责人 80334** | **200** | **200** ✅ |
| **Q5** | **同角色同事 80335** | **403** | **403** ✅ |
| Q5b | 跨租户其他会员 | 拒绝 | 拒绝 ✅ |
| Q5c | 匿名 | 401 | 401 ✅ |

> Q5b 的返回形态值得记录：跨租户请求被**框架租户边界**拦下时返回
> `HTTP 200 + body {"code":403}`，与文件模块自身的 `HTTP 403` 不同 —— 断言需兼顾两种形态。

---

## 3. OSS 分流：前提已验证，**未切换**

按约定 master 暂不切换，因此本轮只做「能不能切」的前提验证：

| 项目 | 结果 |
|---|---|
| 配置 | ID 36 = `S3FileClientConfig`，`oss-cn-beijing.aliyuncs.com` / 桶 `java-ai-comency`，`enablePublicAccess=true` |
| 凭据 | 容器内 `OSS_ACCESS_KEY` / `OSS_ACCESS_SECRET` **已注入**（分别 24/30 字符） |
| 桶可达 | 根路径匿名访问 → **403**（桶存在、禁止匿名列举，符合预期） |
| **可写** | `GET /admin-api/infra/file-config/test?id=36` → **code=0**，返回 `https://java-ai-comency.oss-cn-beijing.aliyuncs.com/public/3b5142fb….jpg` |
| **公开可读** | 该对象匿名 GET → **HTTP 200 / image/jpeg / 18385 B** ✅ |
| master | 仍为 **ID 4**（36 为 0），已复核 |

> 注：`file-config/test` 会在 OSS 上留一个测试对象
> （`public/3b5142fb691344fabbe342cd59fca333.jpg`），清理时一并删除即可。

**未做（需你确认后再推进）**：
1. 应用侧「公开文件内容复制到 OSS」的迁移器（未实现）。
2. 把已确认公开记录的 `config_id` / `url` 批量切到 36（等于事实切换）。
3. master 切换到 36（你明确要求暂不切换）。

> ⚠️ 一个实测约束（**本轮真的踩到了**）：改 `infra_file_config.master` 后，infra 的
> **master client 有缓存**。我为了验证「能否写到 36」临时把 master 切成 36、等 75 秒、
> 再把库改回 4 —— 但**缓存停在 36 没有回退**，随后上传开始返回 OSS 直连 URL，
> 回归脚本因解析不到 `configId` 而失败。
> **处置**：`docker compose … restart infra` 让缓存按库重建，上传恢复 `…/infra/file/4/get/…`。
> **结论**：真正切换 master 必须**重启 infra 容器**，且回滚 likewise —— 不能只改库。

> 遗留：验证期间在 OSS 上留下两个对象
> （`public/3b5142fb….jpg` 来自 `file-config/test`，以及一次误写到 OSS 的测试图），
> DB 记录已删除，OSS 对象需手动清理。基线已复核回到 **241（189/52）**。

---

## 4. 回归结果：64/64 PASS

```
C0–C14   基础访问控制（含公开口径 189/189 匿名 200）
M0–M9b   真实会员通道（11 例）
N0–N4c   询价端到端（绑定成功 / 失败回滚）
P1–P2c   两条边界（缓存会话隔离 / 无归属文件禁认领）
Q0–Q5c   售后端到端 + 店铺客服授权（本轮新增 11 例）
```

库基线：**存活 241（公开 189 / 私有 52）**，售后单残留 0、
店铺负责人 `NULL`、订单项 `after_sale_status=0`，夹具（会员口令 / 采购成员 / 头像）全部还原。

---

## 5. 未测项（明确单列，不计入"通过"）

| # | 未测项 | 状态与原因 |
|---|---|---|
| 1 | **H5 交互级上传**（点头像「修改」→ 选文件 → 回显/预览） | 无头环境驱动不了 uni-app H5 的 `chooseImage`：它动态创建 `visibility:hidden` 的 input，MutationObserver / patch `HTMLInputElement.click` / CDP `upload` **均捕获不到**（`window.uni` 是空对象，真实 API 走 ESM 不挂全局）。**机制层已验证**：浏览器内同源 fetch 证明「改写 `/admin-api`→`/app-api` + Bearer → 200 且生成 blob；不带 token → 401；原始 `/admin-api` 配会员 token → 500」。需手工补验，步骤见 §6。 |
| 2 | **Android 真机**上传 / 回显 / 预览 | 无设备。步骤见 §6。 |
| 3 | `FILE_BIND_BIZ_CONFLICT`（重绑他人单据）黑盒用例 | `FileApi` 走 `/rpc-api` 不经网关，无法用 HTTP 构造。仍为代码走查。 |
| 4 | 违规转存的自动化巡检 | 无巡检任务，只有回归脚本里的 C14 口径校验。 |
| 5 | OSS 实际分流 | 见 §3，前提已验证、切换未执行。 |

---

## 6. 手工验收步骤（无 Android 设备 / 无头环境跑不了交互时照此执行）

### 6.1 H5（浏览器，桌面 Chrome 即可）
1. 打开 `http://localhost:3000/pages/user/info?tenantId=2001`
   （**history 模式，用路径不要用 hash**）。
2. 账号登录：账号 `13920010001`、密码 `admin123`，**勾选"我已阅读并同意遵守"**，点登录。
3. 点头像下方「修改」→ 选一张 PNG。
4. **断言**：
   - 上传期间 Network 出现 `POST /app-api/infra/file/upload` → 200；
   - 回显的 `<image>` / `<img>` 的 `src` **不是** `/admin-api/infra/file/...` 裸地址，
     而应是 `blob:` 或本地路径；
   - 未解析完成前显示占位（不出现 401 破图）。
5. 点「保存」→ 该头像应变为**公开**（`access_type=1 / bind_status=2`），
   退出登录后匿名访问该 URL 应 **200**。
6. 退出登录 → 换 `13920010002` 登录 → 打开同一页面，**不应**看到上一账号的图片缓存。

### 6.2 Android 真机（**WebView 容器，不是 uni-app 原生包**）

> ⚠️ 上一版这里写错了，已更正：
> `txgy-mall-android` 是**现有移动 H5 的原生 WebView 容器**（包名 `cn.txgy.mall`，
> `MainActivity` 内 `browser = new WebView(this)` + `browser.loadUrl(target.entryUrl)`），
> **商城地址在安装后由使用者选择**（输入框 hint：`http://电脑局域网IP:3000`）。
> 项目**没有** `pnpm build:app-android` 命令，uni-app 侧只有 `pnpm build:h5`。
> 完整安装/证书/构建说明见 `docs/mall-android-signing-20260924.md` 与 `txgy-mall-android/README.md`。

**结论：仅更新 H5 页面通常不需要重新打 APK** —— 容器加载的是 H5 站点，
改完 H5 并重新部署 `mall-h5` 后，App 里刷新即可看到新页面。
只有容器本身（原生代码）改动才需要重新打包签名。

验收步骤：
1. 安装 APK（PC 下载页随附的签名 APK，或维护者本地构建：
   `txgy-mall-uniapp-master\scripts\prepare-android-signing.ps1` →
   `txgy-mall-android\build.ps1 -SdkRoot '你的Android SDK目录'`，不带 `-Publish` 只生成本地构建）。
2. 手机与联调机在同一局域网；App 内填入商城地址 `http://<电脑局域网IP>:3000`。
3. 用 `13920010001 / admin123` 登录（选择租户 2001）。
4. **上传**：我的 → 头像「修改」→ 拍照或从相册选图。
5. **回显**：上传完成后头像应立即显示（H5 内走 `uni.downloadFile` 带 token → 本地路径）。
6. **预览**：点头像放大，确认 `uni.previewImage` 用的是本地路径而非裸 URL。
7. **售后凭证**：订单 → 申请售后 → 上传凭证 → 提交 → 售后列表/详情里能看到凭证图。
8. **断言**：抓包（Charles/mitmproxy）确认
   - 上传带 `Authorization` 与 `tenant-id`；
   - 读图走 `/app-api/infra/file/...`（**不是** `/admin-api`），带 `Authorization`；
   - 换账号登录后同一地址返回 **403**，且页面不再显示旧图。
9. **回归**：杀进程重进 → 图片重新拉取（缓存不跨会话）。

> 若必须验证"原生容器 + 新 H5"的组合（例如升级了容器），才需要重打 APK；
> 日常 H5 改动按上面第 2 步刷新即可。

---

## 7. 从源码 / SQL / Docker 复现

### 7.1 SQL（按顺序，幂等）
```
deploy/mall/sql/001-schema.sql
deploy/mall/sql/002-demo.sql
deploy/mall/sql/005-ready.sql
deploy/mall/sql/006-file-access-control.sql      # 访问控制列 + 默认私有
deploy/mall/sql/007-legacy-public-image-whitelist.sql
deploy/mall/sql/008-file-upload-ticket.sql       # 一次性上传凭证表
deploy/mall/sql/009-file-public-recompute.sql    # 公开口径按真实业务引用重算
```
进入 `deploy-mysql-1` 执行：
`docker exec -i deploy-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot ruoyi-vue-pro' < 脚本`

### 7.2 镜像：必须从源码重建（tag 无法拉取）
补丁镜像只在既有基础镜像上替换 jar / 前端产物，**仓库里没有、也推不上去**。
一键重建：
```bash
bash deploy/patch-image/build-all.sh            # 全量
bash deploy/patch-image/build-all.sh backend    # 只后端
bash deploy/patch-image/build-all.sh frontend   # 只前端
```
脚本内含完整基础镜像链与三个踩坑开关（`MSYS_NO_PATHCONV=1`、不写 `# syntax=`、product 的 jar 路径差异）。

### 7.3 上线 / 回滚
```bash
cd deploy && docker compose -p deploy \
  -f docker-compose.yml -f docker-compose.mall-rollout.yml \
  -f docker-compose.mall-android-rollout.yml \
  -f docker-compose.file-acl-rollout.yml -f docker-compose.file-acl-frontend.yml \
  up -d --no-build infra trade product member mall-pc mall-h5 frontend
```
**回滚**：去掉最后两个 `-f` 覆盖层重新 `up -d`。

### 7.4 回归
```bash
python deploy/mall/verify-file-access.py    # 退出码 0 = 全通过；输出 64/64
```
脚本自带夹具保存/还原（会员口令、企业采购成员、店铺负责人、订单项售后状态），
重复运行不会污染库。

---

## 8. 本轮上线清单

| 组件 | 镜像 tag | 变化 |
|---|---|---|
| infra | `deploy-infra:file-acl-20260924-3` | 无变化（上轮） |
| **trade** | **`mall-trade:file-acl-20260924-3`** | **新增店铺负责人参与方** |
| product | `mall-product:file-acl-20260924-2` | 无变化 |
| member | `mall-member:file-acl-20260924-2` | 无变化 |
| **H5** | **`txgy-mall-h5:file-acl-20260924-4`** | **修复 API 基址污染** |
| PC | `txgy-mall-pc:file-acl-20260924-1` | 无变化 |
| **admin** | **`txgy-mall-admin:file-acl-20260924-2`** | **修复 API 基址污染** |

DB：无新迁移；`infra_file_config` master 未动（4=1 / 36=0）。
