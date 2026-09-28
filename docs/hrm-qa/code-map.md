# HRM 代码梳理总览

> 详细分册：`code-map-recruit.md` · `code-map-employee.md` · `code-map-payroll.md` · `code-map-portal-security.md`

## 1. 模块结构

| 层 | 位置 | 规模 |
| --- | --- | --- |
| RPC API | `txgy-module-hrm/txgy-module-hrm-api` | 员工合规/查询 API |
| 服务端 | `txgy-module-hrm/txgy-module-hrm-server` | ~1013 Java 文件，96 Controller |
| 管理前端（本仓规范） | `txgy-ui-admin-vben/apps/web-antd/src/views/hrm/**` | 约 230+ Vue 页面；`web-ele` 等为镜像应用，测试以 web-antd 为准 |
| 静态路由 | `.../router/routes/modules/hrm.ts` | 详情页/配置页 hideInMenu |
| 菜单/权限种子 | `sql/hrm_menu.sql`、`sql/2026-09-*-hrm-*.sql` | `hrm:*` permission |

### 后端包分域

```
controller/admin/{attendance,certificate,company,contract,employee,employment,
  home,insurance,operatelog,performance,policy,portal,recruit,safety,salary}
service/   — 与上面对应 + job/{certificate,contract,employee,performance,recruit,salary}
dal/{dataobject,mysql,redis}
framework/{security,datapermission,excel,operatelog,rpc,web,agent}
```

## 2. 关键状态机（摘要）

| 领域 | 状态机 | 关键落点 |
| --- | --- | --- |
| 招聘职位 | `canChangeStatus` | `HrmRecruitPostServiceImpl:204-213` |
| 候选人 | `isStatusTransitionAllowed` | `HrmRecruitCandidateServiceImpl:272-292` |
| 申请/流程 | `isAllowed` | application `:586-673` |
| Offer | DRAFT→审批→CONFIRMED→onboard | `HrmRecruitOfferServiceImpl:112-571` |
| 员工在职状态 | 在职/待入职/待离职/已离职 | `HrmEmployeeEntryStatusEnum:21-24` |
| 异动 | 预约生效 + row lock | `HrmEmployeeServiceImpl:505-564` |
| 合同签署 | PENDING→SIGNED/INVALID | `HrmContractSignRecordServiceImpl:75-284` |
| 证件预警 | VALID/EXPIRING/EXPIRED | `HrmCertificateWarningServiceImpl:117-218` |
| 请假 | BPM + 手动审批 | `HrmAttendanceLeaveServiceImpl:48-230` |
| 工资月表 | UNCOMPUTED→COMPUTED→HISTORY | `HrmSalaryMonthRecordStatusEnum:19-21` |
| 社保月表 | UNARCHIVED↔ARCHIVED | `HrmInsuranceMonthStatusEnum:19-20` |
| 绩效计划 | DRAFT→NOT_STARTED→RUNNING→ARCHIVED\|TERMINATED | `HrmPerformancePlanServiceImpl:268-360` |
| 绩效考核 | NOT_PROCESSED→PENDING→PROCESSED\|REJECTED\|APPEALED | process `:191-891` |

## 3. 跨模块数据流

```text
需求(demand) → 职位(post) → 简历/投递(resume/application)
    → 候选人投影(candidate) → 面试(interview) → Offer
    → 入职(onboard): employee + 工号 + 账号 + 合同草稿/待签
         + demand.onboardCount 回写

考勤打卡/请假 → 月度考勤统计 → 工资月表核算(扣款项)
社保月表(个人/单位) → 工资社保扣缴
计件记录 → 工资 mergePieceworkSalary
绩效系数 → applyPerformanceSalary
薪资档案/调薪 → 下月工资基数
```

## 4. 并发 / 幂等要点

| 点 | 现状 | 风险 |
| --- | --- | --- |
| Offer 接受/审批 | `selectByIdForUpdate` + version CAS | 较好 |
| Offer 审批回调 | processInstanceId+version 重放安全 | 较好 |
| 入职 onboard | 已存在 employeeId 则早返回 | 较好 |
| 需求编制核增 | check-then-increment | **并发可超编**（`OfferServiceImpl:542` 校验 vs `:566` 自增，中间 `:561` createEmployee） |
| 工资月表创建 | 行锁，但无 year-month 唯一 | 可重复建月 |
| 批量导入/发薪条 | 无幂等键 | 重复提交风险 |
| `syncSalaryOption` | 仅 JVM `synchronized` | 多实例失效 |
| 工号生成 | 序列行锁 + 唯一键 | 较好 |

## 5. 权限面

- 管理端：`hrm:employee:*` / `hrm:recruit:*` / `hrm:attendance:*` / `hrm:salary:*` / `hrm:insurance:*` / `hrm:performance:*` / `hrm:contract:*` / `hrm:certificate:*` 等。
- 门户端：大量 `HrmPortal*` 仅 `isAuthenticated()` 或缺失 `@PreAuthorize`（简历、简历版本、招聘代理、投递写操作）。
- 数据权限：`HrmDataPermissionConfiguration` 对员工/考勤/绩效/合同/证件做部门过滤，**明确排除** salary、insurance、recruit、个人简历。
- 公开职位板：`@TenantIgnore` 跨租户设计（可接受，需产品确认）。

## 6. 前端路由 vs 菜单 component

| 菜单 component（Vue 目录） | 实际路由 | 说明 |
| --- | --- | --- |
| `hrm/salary/config/attendance-rule/index` | `/hrm/salary/attendance-rule` | 目录带 config，URL 不带 |
| `hrm/salary/config/rate-limit/index` | `/hrm/salary/rate-limit` | 同上 |
| `hrm/salary/config/{group,tax-rule,config,option,change-template}` | `/hrm/salary/config/...` | URL 与目录一致 |
| 门户招聘 | `/portal/recruit`（非 `/hrm/portal/recruit`） | 管理端侧边栏无此入口 |

侧边栏实测 href 列表见 `evidence/sidebar-routes.json`。

## 7. 代码级风险索引（详见分册）

1. 并发入职可突破需求编制（recruit，`:542`/`:566`）
2. Offer `validUntil` 空=长期（send）vs confirm 强制非空（recruit，`:439-440`）；DRAFT 免审批发放为业务口径非缺陷
3. 需求状态无转移矩阵；双轨 application/legacy 状态（recruit）
4. 转正双路径无锁；定时离职不阻塞未完成交接（employee）
5. 合同 EXPIRED 不会自动置位（employee）
6. 工资月表缺 year-month 唯一；未入薪资组静默丢人（payroll）
7. 门户简历/代理/投递缺 `@PreAuthorize`（依赖 getLoginUserId 内隐归属）（portal-security）
8. actuator/druid permitAll（portal-security）
9. 员工证件/培训仅 isAuthenticated（portal-security）
10. 薪资/社保/招聘数据权限为**刻意排除**（`HrmDataPermissionConfiguration:48-50`）
