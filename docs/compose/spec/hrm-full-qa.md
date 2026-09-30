---
feature: hrm-full-qa
status: delivered
updated: 2026-09-25
branch: master（会话隔离禁止 worktree，cwd 未跟踪交付）
commits: # 未向 master 提交；交付物为未跟踪文件（见 Report）
---

# HRM 全量 QA（代码走查 + 功能测试 + 改进建议）

## Report

**What was built** — 对秦川租户 HRM 完成深度代码梳理（四分册+总览）+ 全量 UI 巡检（323 条）+ 主线/回归/权限探针（32 条）+ 结构化测试结果与改进建议。问题 21 条已定级（严重 10 · 一般 10 · 建议 1；致命 0）+ 1 条未覆盖；改进建议 25 条（流程/体验/新增，均含页面或 API 级落点与验收标准）。核心风险：并发入职可超编（Q-001，`:542`/`:566`）；Offer 有效期 send/confirm 口径不一致（Q-002；DRAFT 免审批为业务口径非缺陷）；门户写接口缺显式鉴权与任务 IDOR（Q-005/006）；工资月表可重复建月（Q-012）；actuator/druid 暴露（Q-008）。上轮仍在：职位无草稿、管理端请假无新增、KPI 角标、考勤组班次、核算就绪非阻断等。独立评审后已修正 T2 权限差异表、Q-002 误报、行号与统计。

**Verification** — `node docs/hrm-qa/scripts/run-phase1-full.mjs`（323 行落盘）· `run-phase2-e2e.mjs`（32 行）· `probe-sidebar-routes.mjs`（侧边栏 href 对照）· `node --check assets/js/report.js` + 条目计数 Q21/I25 · 代码梳理四分册并行产出并汇总 `code-map.md` · 独立评审 critical 9 项复审 PASS（T2 表、Q-002 口径、行号、统计、web-antd、Q-005/007、严重度、typo）。

**Journey log** — 1) 会话隔离仍禁止 `git worktree add`，cwd 未跟踪交付。2) 登录需 combobox 筛「秦川」后点 `[role=option]`，勿再点租户按钮。3) 菜单 component 目录（`salary/config/*`）≠ URL（`/hrm/salary/attendance-rule|rate-limit`），勿按目录拼路由。4) 正则误把正文 `5000` 当接口 500，问题单已人工剔除。5) Offer/编制/门户鉴权类缺陷必须靠代码走查才能挖到，纯 UI 点不够。

## [S1] Problem

HRM 覆盖门户招聘、员工工作台与后台管理，菜单面广（组织/招聘/员工/合同/证件/考勤/薪资/社保/绩效/政策等约 50+ 子页），业务链路长（招聘 → 入职 → 转正 → 调岗 → 考勤/薪资 → 离职）。2026-09-23 曾交付一轮全量 QA（29 条问题），但缺少：

1. 本轮独立复测结论（代码是否变化、问题是否仍在）；
2. 系统化**代码梳理**（模块结构、领域边界、接口面、数据流转契约）；
3. 可逐条跟进的**改进与优化建议**（流程改进、体验优化、建议新增功能）。

## [S2] Design

### 测试环境

| 项 | 值 |
| --- | --- |
| 租户 | 秦川（system_tenant.id=2020） |
| 超管 | `qinchuan` / `admin123` |
| HR | `qinchuan_hr` / `admin123`（userId=80950，角色 `hr_role_2020`） |
| 管理端 | http://localhost:5173 （txgy-ui-admin-vben / web-antd） |
| 网关 | http://localhost:48080 （`/admin-api`） |
| 登录契约 | Header `tenant-id: 2020` + body `{username,password}` |
| 后端 | `txgy-module-hrm`（api + server，约 1000 Java 文件、96 Controller） |
| 前端 | `txgy-ui-admin-vben/apps/web-antd/src/views/hrm/**` + 门户 `portal` |

### 工作区覆盖（环境约束）

会话隔离禁止 `git worktree add`（共享 ref store 保护）。本轮与上轮相同：

- 在仓库 cwd 以**未跟踪文件**交付 Spec、测试记录与报告；
- **不向 master 提交**产品代码或报告；
- 不创建 `.worktrees/*`。

### 执行策略（已确认：全量复测 + 深化改进建议；Spec 已按反馈扩大测试面 / 加深代码梳理 / 细化建议）

1. **代码梳理（深度）**：按领域（组织/招聘/员工/合同/证件/考勤/薪资/社保/绩效/门户/审批）梳理，并**必须**覆盖：
   - 后端服务、Controller、关键 Mapper/表结构对照；
   - 核心**状态机**（职位、候选人阶段、Offer、合同签署、入转调离、考勤请假、工资表/工资条）；
   - **并发与幂等**风险点（Offer 接受、入职落库、工资核算、审批回调）；
   - **权限点**（`hrm:*` permission 串、门户 vs 管理端隔离、数据可见性）；
   - 前端路由/菜单 component 映射与真实页面一致性；
   - 跨模块数据流契约（招聘→员工、考勤→薪资、社保→扣缴、绩效→奖金）。
2. **第一阶段全菜单功能测试（扩大）**：遍历 HRM **全部**菜单（含设置类、隐藏/次要、门户端、招聘代理入口若有），每页检查矩阵扩大为：
   - 新增 / 编辑 / 删除 / 详情 / 启停用；
   - 查询（组合条件、模糊、分页、排序、空结果、重置）；
   - 导入 / 导出 / 下载模板 / 上传附件；
   - 审批链路（提交/通过/驳回/撤回/去向）；
   - 状态流转按钮（草稿/发布/暂停/关闭/接受/拒绝/过期等）；
   - 角色权限（超管 vs HR vs 员工/门户）与越权探测；
   - 表单校验（必填、边界值、重复提交、日期区间）；
   - 刷新/返回后数据一致性、错误提示可读性。
3. **第二阶段全业务流程测试**（使用者视角 E2E，扩大异常与衔接）：
   - **主线 A 招聘**：需求提报 → 审批/驳回/取消 → 职位草稿/发布/暂停/关闭 → 门户投递/简历 → 候选人筛选/人才库 → 沟通/面试安排/评价 → 录用决策（含淘汰）→ Offer 发放/接受/拒绝/过期 → 入职办理 → 看板漏斗；
   - **主线 B 生命周期**：组织/编制 → 入职建档/工号/合同/账号绑定 → 转正 → 调岗/晋升/降级 → 考勤组/打卡/请假加班审批 → 薪资档案/调薪/月度核算/工资条 → 社保方案/缴纳 → 离职（审批/交接/结算/账号停用/归档）；
   - **衔接检查**：A→B 入职后档案/工号/合同/账号是否自动就绪；考勤→计薪、社保→扣缴、绩效→工资是否自动带数；有无重复录入。
   - **边界与异常**：并发操作、非法状态跳转、缺失配置（未入薪资组/无考勤组）、审批中途取消、跨月数据。
4. **只出报告与建议，不修改业务代码**。

### 测试面（第一阶段，自上而下；含设置/次要/门户/代理入口）

| 菜单分组 | 子菜单 |
| --- | --- |
| 工作台 | HR 工作台 `home`、团队工作台 `team-home`、待办/角标（若有） |
| 组织 | 组织管理 `dept`、部门编制 `headcount`、用工主体信息 `company` |
| 招聘管理 | 招聘需求 `recruit/demand`、招聘沟通 `recruit/chat`、职位类型 `recruit/post-type`、候选人 `recruit/candidate`、招聘职位 `recruit/post`、招聘代理/任务（若有） |
| 招聘设置 | 渠道 `recruit/settings/channel`、淘汰原因 `recruit/settings/eliminate-reason`、招聘企业 `recruit/settings/company`、企业相册/位置（若有） |
| 员工管理 | 员工档案 `employee/list`、员工配置 `employee/config`、用工类型 `employee/employment-type`、黑名单 `employee/blacklist`、入转调离审批 `employee/approval`、工号规则 `employee/no-rule`、异动记录/交接（若有） |
| 合同管理 | 合同模板 `contract/template`（含模板包/预览）、签订记录 `contract/sign-record`、到期预警 `contract/expire`、合同文件台账（若有） |
| 资格证件 | 证件台账 `certificate/ledger`、证件类型 `certificate/type`、到期预警 `certificate/warning` |
| 安全与政策 | 安全教育与体检 `safety`（教育+体检）、政策制度 `policy` |
| 考勤管理 | 打卡记录 `attendance/clock`（含照片/补卡若有）、请假 `attendance/leave`、月度考勤 `attendance/month` |
| 考勤设置 | 考勤组 `attendance/config/group`（班次/打卡时段/扣款）、节假日 `attendance/config/holiday` |
| 社保管理 | 社保缴纳 `insurance/month-record`、员工社保资料；配置：社保方案 `insurance/config/scheme`、缴费标准（若有） |
| 薪资管理 | 考勤计薪规则 `salary/attendance-rule`、工资表 `salary/month-record`、计件工资 `salary/piece-record`、历史工资 `salary/history`、薪资档案 `salary/employee-info`、工资条 `salary/slip`/发放记录、调薪记录 |
| 薪资设置 | 薪资组、计税规则、计薪设置、工资表设置、调薪模板、比例上限/单位联动（若有） |
| 绩效管理 | KPI 考核 `performance/plan`、绩效档案 `performance/assessment`；配置：考核模板、结果模板 |
| 员工端/门户 | 招聘门户 `/portal/recruit`（职位/投递/沟通/Offer/简历版本）、工作台 `portal/home`、我的档案/联系人/教育/工作/培训、我的社保、我的工资条、我的考勤、绩效任务/档案、离职申请（若有） |

### 每页检查矩阵（存在则测，扩大）

- 基础 CRUD：新增 / 编辑 / 删除 / 详情 / 启停用 / 批量
- 查询：组合条件、模糊、分页、排序、空结果、重置、默认筛选与角标一致性
- 数据交换：导入 / 导出 / 模板下载 / 附件上传预览
- 流程：审批提交/通过/驳回/撤回/催办；状态机按钮合法性
- 表单：必填、边界值、非法格式、重复提交、开始结束日期
- 权限：超管 / HR / 员工三角色可见性与操作差异；直接 API 越权抽查
- 一致性：保存后列表刷新、返回再进状态、跨入口同一数据

### 问题与建议契约

**测试结果清单**每条字段：

| 字段 | 说明 |
| --- | --- |
| ID | `T-xxx` 功能点记录 / `Q-xxx` 问题 |
| 阶段 | 第一阶段 / 主线A / 主线B |
| 菜单或环节 | 路径或流程节点 |
| 功能点 | 具体操作 |
| 状态 | 通过 / 失败 / 异常 / 未覆盖 |
| 严重程度 | 致命 / 严重 / 一般 / 建议（仅问题） |
| 复现步骤 | 失败/异常必填 |
| 预期/实际 | 失败/异常必填 |
| 代码定位 | 文件:行或模块路径 |
| 上轮对照 | 新增 / 仍在 / 已修复 / 不适用 |

**改进建议**每条字段（颗粒度要求：落到页面/接口/字段级，并给出验收标准）：

| 字段 | 说明 |
| --- | --- |
| ID | `I-xxx` |
| 类型 | 流程改进 / 体验优化 / 新增功能 |
| 关联问题 | `Q-xxx` 或环节 |
| 现状 | 当前行为（含具体页面路径 / API / 字段名） |
| 建议 | 具体改法（操作步骤或代码落点：文件/接口/字段） |
| 验收标准 | 可观测、可回归的通过条件（Given/When/Then 或明确断言） |
| 价值 | 为何更简单/更便利（减少几步、防哪类错） |
| 优先级 | P0 / P1 / P2 |
| 涉及面 | 前端 / 后端 / 数据 / 流程 / 权限 |
| 工作量 | S / M / L（粗估） |

### 错误分级

- 页面崩溃、接口 5xx、白屏、主链路阻断 → **致命**
- 校验缺失、数据不刷新、权限越权、状态机错误 → **严重**
- 文案/体验/边界提示不当 → **一般**
- 可优化项 → **建议**

### 交付物

| 路径 | 内容 |
| --- | --- |
| `docs/hrm-qa/test-results.md` | 结构化测试结果清单（含代码梳理摘要） |
| `docs/hrm-qa/improvements.md` | 改进与优化建议清单 |
| `docs/hrm-qa/test-records.md` | 过程记录（菜单巡检 + E2E） |
| `docs/hrm-qa/code-map.md` | 代码梳理：模块/领域/接口/数据流 |
| `index.html` + `assets/css/report.css` + `assets/js/report.js` | 可筛选汇总报告（问题 + 建议） |
| `docs/hrm-qa/scripts/*`、`docs/hrm-qa/evidence/*` | 自动化脚本与证据 |

## [S3] Out of Scope

- 修改 HRM 业务代码或前端代码
- 移动端 uni-app 全量覆盖（仅门户数据流相关时抽查）
- 性能压测、安全渗透（仅做角色可见性/越权探测）
- 非秦川租户数据
- 背调等产品未实现能力（记「未覆盖」而非缺陷）

## Tasks

- [x] T1: 代码梳理 code-map.md（深度） — acceptance: 含领域服务/Controller/表结构对照、状态机、并发幂等点、权限点、前后端路由映射、跨模块数据流 (covers: S2)
- [x] T2: 菜单树与角色权限基线 — acceptance: 超管/HR/员工菜单可见性差异表 (covers: S2)
- [x] T3: 第一阶段全菜单功能点测试（扩大矩阵） — acceptance: 每个子菜单按扩大矩阵记录，失败含复现与代码定位 (covers: S2)
- [x] T4: 主线 A 招聘 E2E（含异常/状态机） — acceptance: 需求→入职 + 驳回/取消/Offer 异常分支有证据 (covers: S2)
- [x] T5: 主线 B 生命周期 E2E（含衔接/边界） — acceptance: 组织→离职 + 转正/调岗/考勤/薪资/社保衔接与缺失配置边界有证据 (covers: S2)
- [x] T6: 结构化测试结果清单 test-results.md — acceptance: 字段齐全、可逐条跟进、含上轮对照 (covers: S2)
- [x] T7: 改进与优化建议 improvements.md — acceptance: 三类建议均落到页面/API/字段级，每条含验收标准/优先级/工作量 (covers: S2)
- [x] T8: 汇总报告 index.html — acceptance: 可筛选问题+建议、浏览器可预览、与清单 ID 对齐 (covers: S2)
