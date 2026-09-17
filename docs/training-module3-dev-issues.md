# 培训管理模块三（培训班管理）开发问题总结

> 分支：`dev/tmp-module3-training-plan`
> 基线：`develop`（经 `26318769`、`83a657bb` 两次 merge 同步）
> 功能提交：`5511ae74`（feat(training): add training class plan management module）
> 模块范围：3-1 培训班管理 / 3-2 参训人员动态维护 / 3-3 班级学习进度看板 / 3-4 学习督促 / 3-5 上岗资格核查
> 交付物：后端 5 个接口域（Class/Student/Progress/Remind/Qualification）+ 前端 5 个页面 + 菜单权限 SQL + Gateway 路由修复

---

## 一、问题总览

| # | 问题 | 类型 | 影响环节 | 状态 |
|---|------|------|----------|------|
| 1 | 菜单重构后页面在列表找不到 | 部署/缓存链路 | 前端可见性 | 已解决 |
| 2 | Gateway `training-admin-api` 路由 YAML 缩进错误 | 配置 | 网关转发 | 已解决 |
| 3 | training-server 跨服务 RPC 未显式开启 Feign | 框架 | 站内信/用户信息调用 | 已解决 |
| 4 | 前端本地构建/启动环境问题 | 工程环境 | 前端 dev/build | 已规避 |
| 5 | course 既有页面隐性缺陷（空值/传参） | 存量代码缺陷 | 课程页面展示 | 已顺带修复 |
| 6 | 部分子模块后端接口未交付 | 协作节奏 | 前端页面可运行性 | 已用 demo 层兜底 |
| 7 | 菜单 SQL 重复执行冲突 | 数据脚本 | 菜单初始化 | 已通过幂等化解决 |
| 8 | 分支同步与工作区卫生 | 分支维护 | Git 协作 | 处理中/需自律 |

---

## 二、问题明细

### 1. 菜单重构后页面"在列表找不到"

**现象**

将 `41004 培训管理` 由单页面菜单（type=2）改为目录（type=1），下挂 `41011~41015` 五个页面并配置按钮权限后，管理端列表中找不到新增页面。

**排查链路（三层逐一排除）**

1. 数据库层：SQL 已执行，`system_menu` 中 41004 目录 + 41011~41015 五个页面 + 按钮权限全部到位、挂接正确；
2. 缓存层：yudao 菜单/权限接口存在缓存（Redis `PERMISSION_MENU_ID_LIST`）。本次菜单 SQL 写入发生在 system-server 启动之后约 7 分钟，**SQL 直插不会自动清缓存**，接口仍返回旧菜单树；
3. 实测验证：登录拿 token 后调用菜单接口，确认后端已完整返回（1398 条，含全部新菜单）→ 后端/数据库无问题；
4. 前端层：管理端运行于 nginx 静态镜像容器且**无源码挂载**，镜像构建时间早于新页面代码，容器内 bundle 不含 `training/plan` 子页面。

**根因**

"DB 到位 ≠ 缓存到位 ≠ 前端产物到位"，三层链路中任何一层滞后都会导致页面不可见。

**解决**

重新构建前端镜像 + 等待/清理后端菜单缓存后生效。两者缺一不可。

**教训**

改菜单 SQL 后必须同时处理：后端缓存刷新 与 前端静态产物重建，不能只改数据库。

---

### 2. Gateway `training-admin-api` 路由缩进层级错误

**现象**

`txgy-gateway/src/main/resources/application.yaml` 中 `training-admin-api` 路由缩进层级错误，被错误嵌套在其它模块路由块内（如 fms 的 filters 之后），存在 training 接口经网关转发失效的风险。

**解决**

修正 YAML 缩进，使 `training-admin-api` 与其它模块路由处于同一列表层级（已随功能提交修复）。

**预防**

Gateway 路由采用"复制粘贴改 id/path"的追加方式时，务必检查缩进是否与同级路由对齐，可用 YAML 解析校验。

---

### 3. training-server 跨服务 RPC 未显式开启 Feign

**现象**

培训班督促（站内信）、学员姓名快照、上岗资格核查需要调用 system 服务接口（`NotifyMessageSendApi`、`AdminUserApi`），直接注入失败。

**解决**

新增 `RpcConfiguration`：

```java
@Configuration(value = "trainingRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {
        NotifyMessageSendApi.class, AdminUserApi.class
})
```

**说明**

微服务架构中 Feign client 默认不自动扫描全部接口，模块需显式声明用到的 Api 接口。

---

### 4. 前端本地构建/启动环境问题

工作区调试日志（`_dev-server-out.log` / `_dev-server-err2.log`）记录的典型问题：

| 问题 | 现象 | 处理 |
|------|------|------|
| Node 版本不符 | wanted `^22.18.0 || ^24.0.0`，本机 `v22.16.0` → engine WARN | 升级 Node 或降级依赖约束 |
| pnpm dev 启动失败 | `ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL`，exit 1 | 修复依赖后重试 |
| 环境变量缺失 | `%VITE_APP_TITLE% is not defined in env variables found in /index.html` | 检查 `.env.*` 配置与变量命名 |
| 构建依赖缺失 | `unrun` 未安装 | `package.json` 补充 `unrun: ^0.3.1` |

**注意**：Node 主版本变更会影响本机其它项目，升级前需评估。

---

### 5. course 既有页面隐性缺陷（顺带修复）

开发中复用课程模块页面时发现的存量缺陷：

**`course/index.vue`**
- `COURSE_LEVEL_TEXT[row.level]` 在 `row.level` 为 `undefined` 时无兜底 → 补空值判断，显示 `-`；
- `status` / `mediaType` 列同理补空值兜底；
- 审核驳回原因 `maxlength` 未加 `:` 绑定（写死为字符串）→ 改为 `:maxlength`。

**`course/modules/media.vue`**
- `modalApi.getData()` 实际载荷为 `{ id }` 对象，原代码按 `number` 取值 → courseId 恒为空；
- 修正为 `getData<{ id: number }>()` 并正确取值。

**启示**：跨页面复用时对数据字段做防御性空值处理，避免隐性崩溃。

---

### 6. 部分子模块后端未交付 → "演示数据层"兜底策略

**矛盾**

资源中心、学员学习中心、考试与认证、直播与讲师体系、VR 实训、校企合作、数据统计等子模块后端接口尚未交付，但页面需要保证可运行、可操作、不报错（用于联调/验收/演示）。

**方案**

新增 `demo-page.ts` 统一提供内存演示数据源：

- `createDemoStore<T>(seed)` 提供 `list / page / add / update / patch / remove`；
- 前端各子模块页面正常开发，数据层暂走本地内存；
- 后端接口就绪后**仅替换各 `api.ts` 的实现**，页面（`index.vue` / `data.ts` / `modules/*`）无需改动。

**价值**

将"后端依赖"与"前端开发"解耦，保证页面始终可运行；接口交付后切换成本极低。

---

### 7. 菜单 SQL 重复执行冲突

**现象**

`system_menu` 中历史按钮（`41200~41206`）、旧子菜单（`41011~41015`）可能残留，直接 `INSERT` 会造成主键重复冲突；`41004` 由页面改目录时残留的 `component/permission` 字段影响展示。

**解决（`sql/training_plan_submenu.sql`）**

- 脚本可重复执行（幂等）；
- 先 `DELETE` 限定 id 段（旧按钮 41200~41206、旧子菜单 41011~41015），再 `INSERT`；
- `41004` 改目录时显式置空 `component` / `component_name` / `permission`，设置 `always_show = 1`；
- 约束脚本只操作 `41004 / 41011~41015 / 41200~41208` 段，不影响其它菜单。

---

### 8. 分支维护与工作区卫生

**分支同步**

- 功能开发期间 `develop` 持续并入课程管理（!6/!12）、资源中心（!18）等模块（多人协作），分支需**两次手工 `merge origin/develop`** 防止基线漂移；
- 合并前先 `fetch` 比对 `HEAD..origin/develop`，确认无遗漏再 merge。

**工作区卫生**

调试过程遗留的无关产物需人工甄别，避免误提交：

- `$null`：PowerShell 输出重定向误创建的空文件（`mysql ... > $null` 被当成文件名）；
- `.playwright-cli/`：浏览器自动化缓存目录；
- `_dev-server-*.log` / `_stub-*.log`：本地调试日志。

建议：此类文件写入 `.gitignore` 或及时清理。

---

## 三、排查方法论沉淀

本项目"页面不可见"类问题的标准排查顺序：

1. **数据库层**：SQL 是否执行、数据是否完整、挂接是否正确；
2. **后端缓存层**：菜单接口是否有缓存、写入时间 vs 服务启动时间；
3. **接口实测**：用真实登录态调用接口，确认返回内容（用数据说话，避免臆测）；
4. **前端产物层**：容器是源码挂载还是静态产物？镜像构建时间是否早于代码变更？

> 一句话：先分清"问题在哪一层"，再对症处理，避免盲目重启/重装。

---

## 四、后续开发 Checklist（供参考）

- [ ] 改动菜单/权限 SQL 后：确认后端缓存刷新 + 前端镜像重建；
- [ ] 新增 Gateway 路由：检查 YAML 缩进与同级对齐；
- [ ] 新增跨服务调用：确认 `@EnableFeignClients` 已声明对应 Api；
- [ ] 复用既有页面：对数据字段做空值兜底；
- [ ] 子模块后端未就绪时：数据层走 `demo-page.ts`，接口就绪后只换 `api.ts`；
- [ ] 菜单脚本保证幂等：先 DELETE 限定段再 INSERT；
- [ ] 分支开工前/合入前：`git fetch` + 比对 develop，及时同步基线；
- [ ] 提交前检查：`git status` 甄别 `$null`、缓存目录、日志等无关文件。
