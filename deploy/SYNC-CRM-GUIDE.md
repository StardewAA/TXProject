# CRM 同步指南：报价管理 + 商机阶段

> 适用对象：需要把本地环境同步到最新 CRM（报价管理、商机阶段改动）的组员。
> 最后核对时间：2026-09-15，基准分支 `crm`。

---

## 一、为什么你的环境还是旧的

代码在 `crm` 分支上已经是最新的，但**容器里跑的是编译产物（jar + 前端 dist）**，不是源码。
所以即使 `git pull` 拉到了最新代码，只要没重新构建，页面依然是旧的。

三个常见原因：

| 原因 | 表现 |
|---|---|
| 1. 分支不对（`deploy/team-20260914`） | **完全没有报价管理**，该分支 0 个报价文件，商机阶段还差 119 行 |
| 2. 前端 dist 没重建 | 有报价菜单，但**商机阶段是旧版**（改动全在前端） |
| 3. 后端 jar 没重建 / 报价表没建 | 报价页面报错或查不出数据 |

---

## 二、最快方式：一键脚本

在项目根目录**右键 `deploy/local/sync-crm.ps1` → 使用 PowerShell 运行**，
或在 PowerShell 中执行：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1
```

脚本会自动完成：切分支并拉取 → 构建前端 → 编译后端 jar → 打镜像 → 重启容器 → 导入 SQL → 清缓存 → 验证。

### 可选参数（跳过某些耗时步骤）

```powershell
# 只重新构建前端（商机阶段改动时最常用）
powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1 -SkipBackend -SkipSql

# 代码已经拉过了，跳过 git 操作
powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1 -SkipPull

# 只导入 SQL
powershell -ExecutionPolicy Bypass -File deploy\local\sync-crm.ps1 -SkipPull -SkipFrontend -SkipBackend
```

| 参数 | 作用 |
|---|---|
| `-SkipPull` | 跳过切分支 / 拉代码 |
| `-SkipFrontend` | 跳过前端 dist 构建 |
| `-SkipBackend` | 跳过后端编译 + 打镜像 |
| `-SkipSql` | 跳过数据库修复 |

> 注意：后端编译是在 Docker 容器内用 Maven 构建（本机无需 JDK/Maven），**首次会比较慢**，请耐心等待。

---

## 三、手动步骤（脚本跑不通时用这个）

以下步骤均在**项目根目录**（`d:\防腐保温\txgycloud`）下执行。

### 步骤 0：确认分支

```bash
git branch --show-current
```

- 输出 `crm` → 继续
- 输出其他（尤其是 `deploy/team-20260914`）→ **必须切换**，见步骤 1

### 步骤 1：拉最新代码

```bash
git fetch origin
git checkout crm
git pull origin crm
```

### 步骤 2：重建前端 dist（最容易漏的一步）

报价管理页面、商机阶段 `stage-form.vue` 的改动**全在前端**。

```powershell
cd txgy-ui-admin-vben
pnpm install
pnpm --filter web-antd build
```

### 步骤 3：编译后端 jar + 打镜像

```powershell
cd deploy\local
powershell -ExecutionPolicy Bypass -File build.ps1 -Modules txgy-module-crm/txgy-module-crm-server
powershell -ExecutionPolicy Bypass -File build-images.ps1 -Services crm
```

### 步骤 4：重启容器

```powershell
cd deploy\local
docker compose -f docker-compose.local.yml --env-file ..\.env up -d --build crm
```

前端（若用了主 compose 的 frontend，端口 5173）：

```powershell
cd deploy
docker compose up -d --build frontend
```

### 步骤 5：导入数据库修复

```powershell
cd d:\防腐保温\txgycloud
docker cp deploy\mysql\repair\2026-09-11-crm-quotation.sql txgy-mysql:/tmp/q1.sql
docker exec txgy-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < /tmp/q1.sql"

docker cp deploy\mysql\repair\2026-09-12-crm-customer-type.sql txgy-mysql:/tmp/q2.sql
docker exec txgy-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < /tmp/q2.sql"

docker cp deploy\mysql\repair\2026-09-14-crm-after-sale-visit-record-status.sql txgy-mysql:/tmp/q3.sql
docker exec txgy-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < /tmp/q3.sql"

docker cp deploy\mysql\repair\2026-09-15-crm-business-status.sql txgy-mysql:/tmp/q4.sql
docker exec txgy-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < /tmp/q4.sql"
```

> **第 4 个 SQL 很重要**：商机状态组/阶段（油气管道防腐销售流程、市政保温工程流程、检测技术服务流程及其阶段、赢单率）是**数据库数据，不是代码**，拉代码拉不到，必须执行这个 SQL 才能和最新版一致。脚本会先清掉 `id>=201` 的旧数据再写入，可重复执行。

> 数据库名 `ruoyi-vue-pro`，账号 `root` / `root`。
> 如果你的 MySQL 容器是主 compose 的 `deploy-mysql-1`，把上面 `txgy-mysql` 替换掉即可。

### 步骤 6：清缓存 + 强刷

```powershell
Remove-Item txgy-ui-admin-vben\node_modules\.vite -Recurse -Force -ErrorAction SilentlyContinue
```

浏览器 `Ctrl + Shift + R` 强刷，**或退出重新登录**（菜单缓存需要重新拉取）。

---

## 四、验证是否生效

容器里通常没有 git，用产物本身来判断版本：

```bash
# 后端 jar 里有没有报价管理
docker exec txgy-crm sh -c "unzip -l /app/app.jar | grep -i quotation"
```

```bash
# 前端 dist 里有没有报价页面
docker exec deploy-frontend-1 sh -c "ls /usr/share/nginx/html/js | grep -i quotation"
```

```bash
# 报价表是否已建
docker exec txgy-mysql sh -c "mysql -uroot -proot -e 'SHOW TABLES LIKE \"%quotation%\"' ruoyi-vue-pro"
```

三条都有输出 = 新版本已生效；任何一条无输出 = 对应步骤没做，回到步骤 2 / 3 / 5。

浏览器侧确认：左侧菜单能看到 **报价管理（6800）**、**售后管理（32600）**、**全局检索（32653）**，商机阶段下拉显示的是你改过的阶段。

---

## 五、常见问题速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 根本没"报价管理"菜单 | 在 `deploy/team-20260914` 分支，或 jar / dist 是旧的 | 步骤 0 + 1 + 2 + 3 + 5 |
| 有菜单但商机阶段是旧的 | 前端 dist 没重建 | 步骤 2 |
| 报价页面报错 / 查不出数据 | 报价表没建 | 步骤 5 |
| 容器内 `git: not found` | 正常，jar 部署不含源码 | 用上面的产物验证法 |
| `build.ps1` 报找不到模块 | `-Modules` 要传模块路径，不是 `crm` | 用 `txgy-module-crm/txgy-module-crm-server` |
| 商机阶段下拉和别人不一样 / 阶段、赢单率是旧的 | 商机状态配置是数据库数据，缺 `2026-09-15-crm-business-status.sql` | 步骤 5 第 4 个 SQL |
| 商机阶段改了但页面没变 | 浏览器 / vite 缓存 | 步骤 6 强刷 |

---

## 六、涉及的关键文件

**后端（报价管理，14 个 Java 文件）**
- `txgy-module-crm/txgy-module-crm-server/src/main/java/cn/iocoder/txgy/module/crm/quotation/`

**前端**
- `txgy-ui-admin-vben/apps/web-antd/src/views/crm/quotation/`
- `txgy-ui-admin-vben/apps/web-antd/src/views/crm/business/`（商机阶段 `stage-form.vue`）

**数据库**
- `deploy/mysql/repair/2026-09-11-crm-quotation.sql`（报价表）
- `deploy/mysql/repair/2026-09-12-crm-customer-type.sql`（客户类型字段）
- `deploy/mysql/repair/2026-09-14-crm-after-sale-visit-record-status.sql`（售后回访状态字段）
- `deploy/mysql/repair/2026-09-15-crm-business-status.sql`（**商机状态组/阶段配置数据**：油气管道防腐销售流程、市政保温工程流程、检测技术服务流程）
