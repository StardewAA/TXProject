# HRM Code Map — Attendance / Salary / Insurance / Performance

> Scope: 考勤、薪资、社保、绩效四个子域的控制器 / 服务 / DO、状态机、跨模块数据流、并发幂等、权限串、前端页面、代码级风险。
> 证据以 `file:line` 标注；路径根为仓库根 `D:\桌面\codex\txgycloud\`。
> 生成方式：只读代码扫描，不修改业务代码。

**路径缩写**

| 缩写 | 全路径 |
|------|--------|
| `BE` | `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm` |
| `FE` | `txgy-ui-admin-vben/apps/web-ele/src/views/hrm` |
| `API` | `txgy-ui-admin-vben/apps/web-ele/src/api/hrm` |

---

## 1. Controllers / Services / DOs 总览

### 1.1 Attendance（考勤）

| 子域 | Controller | Service | DO | Mapper |
|------|------------|---------|-----|--------|
| Clock 打卡 | `BE/controller/admin/attendance/HrmAttendanceClockController.java` | `BE/service/attendance/record/HrmAttendanceClockService[Impl].java` | `BE/dal/dataobject/attendance/record/HrmAttendanceClockDO.java` | `BE/dal/mysql/attendance/record/HrmAttendanceClockMapper.java` |
| Leave 请假 | `BE/controller/admin/attendance/HrmAttendanceLeaveController.java` | `BE/service/attendance/record/HrmAttendanceLeaveService[Impl].java` + `listener/HrmAttendanceLeaveStatusListener.java` | `BE/dal/dataobject/attendance/record/HrmAttendanceLeaveDO.java` | `BE/dal/mysql/attendance/record/HrmAttendanceLeaveMapper.java` |
| Group 考勤组 | `BE/controller/admin/attendance/HrmAttendanceGroupController.java` | `BE/service/attendance/config/HrmAttendanceGroupService[Impl].java` | `BE/dal/dataobject/attendance/config/HrmAttendanceGroupDO.java` | `BE/dal/mysql/attendance/config/HrmAttendanceGroupMapper.java` |
| Holiday 节假日 | `BE/controller/admin/attendance/HrmAttendanceHolidayController.java` | `BE/service/attendance/config/HrmAttendanceHolidayService[Impl].java` | `BE/dal/dataobject/attendance/config/HrmAttendanceHolidayDO.java` | `BE/dal/mysql/attendance/config/HrmAttendanceHolidayMapper.java` |
| Statistics 统计 | `BE/controller/admin/attendance/HrmAttendanceStatisticsController.java` | `BE/service/attendance/statistics/HrmAttendanceStatisticsService[Impl].java` | （无独立 DO；聚合 Clock/Leave） | — |

Portal 考勤：`BE/controller/admin/portal/attendance/HrmPortalAttendanceClockController.java`、`HrmPortalAttendanceLeaveController.java`、`HrmPortalAttendanceStatisticsController.java`。

相关枚举：`BE/enums/attendance/record/HrmAttendanceClockStatusEnum`、`HrmAttendanceClockStageEnum`、`HrmAttendanceClockTypeEnum`、`HrmAttendanceClockSourceEnum`、`HrmAttendanceClockButtonStatusEnum`；`HrmAttendanceLeave` 审批状态复用 BPM `BpmProcessInstanceStatusEnum`；`enums/attendance/statistics/HrmAttendanceResultEnum`；`enums/attendance/config/*DeductMethodEnum`（缺勤/迟到早退/漏卡扣款方式）。

### 1.2 Salary（薪资）

| 子域 | Controller | Service | DO | Mapper |
|------|------------|---------|-----|--------|
| month-record 月度工资表 | `HrmSalaryMonthRecordController` + `HrmSalaryMonthEmployeeRecordController` | `service/salary/monthrecord/HrmSalaryMonthRecordService[Impl]` + `HrmSalaryMonthEmployeeRecordService[Impl]` | `HrmSalaryMonthRecordDO` + `HrmSalaryMonthEmployeeRecordDO` | `HrmSalaryMonthRecordMapper` + `HrmSalaryMonthEmployeeRecordMapper` |
| employee-info 定薪 | `HrmSalaryEmployeeInfoController` | `service/salary/employee/HrmSalaryEmployeeInfoService[Impl]` | `HrmSalaryEmployeeInfoDO` | `HrmSalaryEmployeeInfoMapper` |
| change-record 调薪记录 | `HrmSalaryChangeRecordController` | `service/salary/employee/HrmSalaryChangeRecordService[Impl]` | `HrmSalaryChangeRecordDO` | `HrmSalaryChangeRecordMapper` |
| piece 计件 | `HrmSalaryPieceRecordController` | `service/salary/piece/HrmSalaryPieceRecordService[Impl]` | `HrmSalaryPieceRecordDO` | `HrmSalaryPieceRecordMapper` |
| slip 工资条 | `HrmSalarySlipController` + `HrmSalarySlipSendRecordController` + `HrmSalarySlipTemplateController` | `service/salary/slip/HrmSalarySlipService[Impl]` + `HrmSalarySlipSendRecordService[Impl]` + `HrmSalarySlipTemplateService[Impl]` | `HrmSalarySlipDO` + `HrmSalarySlipSendRecordDO` + `HrmSalarySlipTemplateDO` | 三个对应 Mapper |
| tax 计税规则 | `HrmSalaryTaxRuleController` | `service/salary/config/HrmSalaryTaxRuleService[Impl]` | `HrmSalaryTaxRuleDO` | `HrmSalaryTaxRuleMapper` |
| group 薪资组 | `HrmSalaryGroupController` | `service/salary/config/HrmSalaryGroupService[Impl]` | `HrmSalaryGroupDO` | `HrmSalaryGroupMapper` |
| option 工资项 | `HrmSalaryOptionController` | `service/salary/config/HrmSalaryOptionService[Impl]` | `HrmSalaryOptionDO` + `HrmSalaryOptionTemplateDO` | 两个对应 Mapper |
| attendance-rule 耗勤扣款规则 | `HrmSalaryAttendanceRuleController` | `service/salary/attendancerule/HrmSalaryAttendanceRuleService[Impl]` | `HrmSalaryAttendanceRuleDO` | `HrmSalaryAttendanceRuleMapper` |
| payment 银行发放 | `HrmSalaryPaymentController` | `service/salary/payment/HrmSalaryPaymentService[Impl]` + `BankPaymentFileGenerator` + `GenericCsvBankPaymentFileGenerator` | （读工资表生成文件） | — |
| payroll 试算 | `HrmSalaryPayrollController` | `service/salary/payroll/HrmSalaryPayrollService[Impl]` | （无落库） | — |
| rate-limit 比例上限 | `HrmSalaryRateLimitController` | `service/salary/config/HrmSalaryRateLimitService[Impl]` | `HrmSalaryRateLimitDO` | `HrmSalaryRateLimitMapper` |
| change-template 调薪模板 | `HrmSalaryChangeTemplateController` | `service/salary/config/HrmSalaryChangeTemplateService[Impl]` | `HrmSalaryChangeTemplateDO` | `HrmSalaryChangeTemplateMapper` |
| insurance-deduct 社保扣款桥 | `HrmSalaryInsuranceDeductController` | `service/salary/insurancededuct/HrmSalaryInsuranceDeductService[Impl]` | （读 `HrmInsuranceMonthEmployeeRecordDO`） | — |
| performance data 绩效桥 | `HrmSalaryPerformanceController` | `service/salary/performancedata/HrmSalaryPerformanceService[Impl]` | （读 `HrmPerformanceAssessmentDO` / `HrmPerformancePlanDO`） | — |
| config 全局薪资配置 | `HrmSalaryConfigController` | `service/salary/config/HrmSalaryConfigService[Impl]` | `HrmSalaryConfigDO` | `HrmSalaryConfigMapper` |

Portal 工资条：`BE/controller/admin/portal/salary/HrmPortalSalarySlipController.java`。

后台任务：`BE/job/salary/HrmSalaryChangeJob.java`（调薪生效）。

### 1.3 Insurance（社保）

| 子域 | Controller | Service | DO | Mapper |
|------|------------|---------|-----|--------|
| scheme 方案 | `HrmInsuranceSchemeController` | `service/insurance/config/HrmInsuranceSchemeService[Impl]` | `HrmInsuranceSchemeDO` + `HrmInsuranceSchemeProjectDO` | 两个对应 Mapper |
| standard 缴费标准 | `HrmInsuranceStandardController` | `service/insurance/config/HrmInsuranceStandardService[Impl]` | （只读标准表/字典，无专属 DO） | — |
| month-record 月度社保表 | `HrmInsuranceMonthRecordController` + `HrmInsuranceMonthEmployeeRecordController` | `service/insurance/monthrecord/HrmInsuranceMonthRecordService[Impl]` + `HrmInsuranceMonthEmployeeRecordService[Impl]` | `HrmInsuranceMonthRecordDO` + `HrmInsuranceMonthEmployeeRecordDO` | 两个对应 Mapper |
| employee-info 员工参保 | `HrmInsuranceEmployeeInfoController` | `service/insurance/employee/HrmInsuranceEmployeeInfoService[Impl]` | `HrmInsuranceEmployeeInfoDO` | `HrmInsuranceEmployeeInfoMapper` |

Portal 社保：`BE/controller/admin/portal/insurance/HrmPortalInsuranceController.java`。

### 1.4 Performance（绩效）

| 子域 | Controller | Service | DO | Mapper |
|------|------------|---------|-----|--------|
| plan 计划 | `HrmPerformancePlanController` | `service/performance/plan/HrmPerformancePlanService[Impl]` | `HrmPerformancePlanDO` | `HrmPerformancePlanMapper` |
| assessment 考核 | `HrmPerformanceAssessmentController` | `HrmPerformanceAssessmentService[Impl]` + `HrmPerformanceAssessmentProcessService[Impl]` + `HrmPerformanceAssessmentReviewService[Impl]` + `HrmPerformanceAssessmentQueryService[Impl]` + `HrmPerformanceAssessmentActionRecordService[Impl]` | `HrmPerformanceAssessmentDO`、`HrmPerformanceAssessmentStageDO`、`HrmPerformanceAssessmentDimensionDO`、`HrmPerformanceAssessmentQuotaDO`、`HrmPerformanceAssessmentQuotaScoreDO`、`HrmPerformanceAssessmentActionRecordDO`、`HrmPerformanceAssessmentAppealRecordDO` | 对应 7 个 Mapper |
| assessment-template | `HrmPerformanceAssessmentTemplateController` | `HrmPerformanceAssessmentTemplateService[Impl]` | `HrmPerformanceAssessmentTemplateDO` | `HrmPerformanceAssessmentTemplateMapper` |
| result-template 结果模板 | `HrmPerformanceResultTemplateController` | `HrmPerformanceResultTemplateService[Impl]` | `HrmPerformanceResultTemplateDO` | `HrmPerformanceResultTemplateMapper` |

Portal 绩效：`BE/controller/admin/portal/performance/HrmPortalPerformanceAssessmentController.java`（任务列表 / 填指标 / 评分 / 确认 / 申诉 / 处理阶段）。

后台任务：`BE/job/performance/HrmPerformanceAppealTimeoutJob.java`（申诉超时处理）。

---

## 2. 状态机（含 file:line）

### 2.1 Leave 审批（请假）

状态载体：`HrmAttendanceLeaveDO.approvalStatus`，复用 BPM `BpmProcessInstanceStatusEnum`。

```
[createLeave]  BE/service/attendance/record/HrmAttendanceLeaveServiceImpl.java:67
  └─ 校验员工 + 时段冲突（RUNNING/APPROVE 中的请假禁止重叠） :76-78
  └─ 启动 BPM 流程 LEAVE_PROCESS_KEY="hrm_attendance_leave" :48
       ↓
  RUNNING / APPROVE（审批中）
       ↓
  ┌─ approveLeave(id, approved, reason)  :125-143
  │    · 仅当当前为 RUNNING 可人工终审 :125-130
  │    · 已走工作流则禁止人工审批 :134
  │    · 驳回必须填 reason :136-138
  │    · approved → APPROVE；否则 → REJECT
  │
  ├─ cancelLeave  :101-115
  │    · 仅申请人可撤；状态须可撤 :106-110
  │    · 调 processInstanceApi.cancelProcessInstanceByStartUser :115
  │
  └─ BPM 回调 listener/HrmAttendanceLeaveStatusListener.java:16
       └─ updateLeaveApprovalResult :214-230
            · 仅终态（isProcessEndStatus）才写回 :216
```

终态：APPROVE（通过）/ REJECT（驳回）/ CANCEL。通过后参与考勤统计与耗勤扣款（`getApprovedLeaveList` :199）。

### 2.2 Salary Month Record 核算 / 锁定

状态枚举：`BE/enums/salary/monthrecord/HrmSalaryMonthRecordStatusEnum.java:19-21`
`UNCOMPUTED(5 未核算)` → `COMPUTED(11 已核算)` → `HISTORY(10 已归档/锁定)`

```
createMonthRecord  HrmSalaryMonthRecordServiceImpl.java:135-156
  · 初始 status = UNCOMPUTED :156
  · createNextMonthRecord :167-186
      若上月 status != HISTORY → 强制置 HISTORY（归档） :184-186
       ↓
  UNCOMPUTED
       ↓
computeMonthRecord(id, syncInsurance, syncAttendance, attendanceRows)  :218-345
  · validateMonthRecordEditableForUpdate（HISTORY 不可改） :223 / :515-517
  · 汇总员工月记录后 status → COMPUTED :340
       ↓
  COMPUTED（可再 compute 覆盖、可改员工记录）
       ↓
deleteMonthRecord :352-369
  · 仅最新一条可删（NOT_LATEST）
  · 删员工月记录 + 月表
  · 上一月从 HISTORY 回退为 COMPUTED（解锁可再算） :364-369
       ↓
  HISTORY（锁定，禁止编辑/核算/删除）
```

锁定语义：`HISTORY` 即「已归档/锁定」；`validateMonthRecordEditable` / `…ForUpdate` 在 :506-517 拦截。工资条发放要求 `status != UNCOMPUTED`（`HrmSalarySlipSendRecordServiceImpl.java:81-82`）。

### 2.3 Slip 发送（工资条）

无独立状态机枚举；状态由「SendRecord 存在 + Slip.readStatus」表达：

```
sendSalarySlip  HrmSalarySlipSendRecordServiceImpl.java:77-130
  · monthRecord 必须已核算（!UNCOMPUTED） :81-82
  · 模板快照 buildSalarySlipTemplateSnapshot :84-85
  · 筛选员工：all 或指定 employeeIds，且必须都在月表中 :88-98
  · 仅向已绑系统账号（employee.userId != null）发放 :103-116
  · 插入 SendRecord（employeeCount / sendEmployeeCount） :119-123
  · 批量建 Slip，readStatus = UNREAD :HrmSalarySlipServiceImpl.java:75
  · 并发送站内信 sendSingleMessageToAdmin :HrmSalarySlipServiceImpl.java:247-257
       ↓
  Slip: UNREAD → READ（员工门户 markRead）  HrmSalarySlipServiceImpl.java:155
       ↓
deleteSalarySlipSendRecord :157+
  · 删 SendRecord + 关联 Slip + 站内信
```

### 2.4 Insurance Month（社保月）

状态枚举：`BE/enums/insurance/monthrecord/HrmInsuranceMonthStatusEnum.java:19-20`
`UNARCHIVED(0 未归档)` ↔ `ARCHIVED(1 已归档)`

```
createFirstMonthRecord  HrmInsuranceMonthRecordServiceImpl.java:57-70
  · selectLastForUpdate 防重：已有表则 FIRST_MONTH_RECORD_EXISTS :59-60
  · createMonthRecord + computeMonthRecord（生成员工月记录并汇总）
       ↓
  UNARCHIVED（当前月，可改员工记录/项目）
       ↓
createNextMonthRecord :77-98
  · selectLastForUpdate 锁最近月 :79
  · 若上月 UNARCHIVED → archiveMonthRecord（ARCHIVED） :85-86 / :236
  · 创建次月 + compute
       ↓
  上月 ARCHIVED（锁定）
       ↓
deleteMonthRecord :105-126
  · 仅最新月可删；至少保留一张 :109-113
  · 删员工记录 + 月表
  · 上一月 ARCHIVED → UNARCHIVED（解锁） :120-123
```

编辑保护：`validateMonthRecordEditableForUpdate` :163-171，ARCHIVED 抛 `INSURANCE_MONTH_RECORD_ARCHIVED`。

员工级状态：`enums/insurance/employee/HrmInsuranceEmployeeStatusEnum`（正常/停缴等），月记录仅取 `getNormalMonthEmployeeRecordMap` 参与扣款。

### 2.5 Performance Plan / Assessment

**Plan**：`enums/performance/plan/HrmPerformancePlanStatusEnum.java:19-23`

```
DRAFT(1) ──start──► NOT_STARTED(2) ──startPerformancePlan──► RUNNING(3)
                      │                                        │
                      │                                        ├─ startPerformancePlanInterview → 下一阶段
                      │                                        ├─ archivePerformancePlan → ARCHIVED(4)
                      │                                        └─ terminatePerformancePlan → TERMINATED(5)
                      └─（创建时也可直接 NOT_STARTED，HrmPerformancePlanServiceImpl.java:117）
```

关键转移：

| 动作 | 位置 | 约束 |
|------|------|------|
| create/update | `HrmPerformancePlanServiceImpl.java:99 / :134` | 可改状态集合 DRAFT/NOT_STARTED/ARCHIVED/TERMINATED :196-199；更新用 `selectOneForUpdate` :139 |
| startPerformancePlan | :268-280 | `selectOneForUpdate` :270；批量启动员工考核 `startPerformanceAssessmentList`；plan → RUNNING |
| startPerformancePlanInterview | :309-312 | 推进面试阶段 |
| archivePerformancePlan | :327-335 | 归档全部考核；plan → ARCHIVED + stageType=ARCHIVED |
| terminatePerformancePlan | :347-360 | 仅 RUNNING 可终止 :351；→ TERMINATED |

**Assessment 员工考核**：

- 处理状态：`HrmPerformanceAssessmentProcessStatusEnum.java:19-20` `PROCESSING(1)` / `FINISHED(2)`
- 阶段状态：`HrmPerformanceAssessmentStageStatusEnum.java:19-24` `NOT_PROCESSED(0)` / `PROCESSED(1)` / `PENDING(2)` / `REJECTED(3)` / `PROCESSING(4)` / `APPEALED(5)`
- 申诉：`HrmPerformanceAppealStatusEnum` / `HrmPerformanceAppealRecordStatusEnum`
- 结果审核：`HrmPerformanceResultAuditStatusEnum`（PENDING/APPROVE/REJECT/CANCEL）

阶段推进（`HrmPerformanceAssessmentProcessServiceImpl.java`）：

```
阶段默认 NOT_PROCESSED :191
  → 激活/到达节点 → PENDING :241 / :253-254 / :333
  → 处理（填指标/评分/确认）→ PROCESSED :413 / :472 / :687 / :962
  → 驳回 → REJECTED :414 / :700 / :803
  → 申诉 → APPEALED :516，同时 assessment.appealStatus=PENDING、processStatus 回 PROCESSING :531-532
  → 结果确认阶段 PROCESSED → processStatus=FINISHED :483 / :763
  → 重开（申诉处理）→ 阶段回 PENDING，processStatus 回 PROCESSING :876-891
```

就绪门槛（`HrmPerformanceAssessmentServiceImpl.java`）：评分前 `PERFORMANCE_SCORING_NOT_READY` :428；面谈前 `PERFORMANCE_INTERVIEW_NOT_READY` :456；归档前 `PERFORMANCE_ARCHIVE_NOT_READY` :484。

超时：`HrmPerformanceAppealTimeoutJob` 按 `HrmPerformanceAppealTimeoutActionEnum` 自动处理。

---

## 3. 跨模块数据流

### 3.1 Attendance → Salary compute

入口：`HrmSalaryMonthRecordServiceImpl.computeMonthRecord` :218。

```
HrmAttendanceStatisticsService.getAttendanceMonthRecordMap(...)
  → 月度出勤汇总（实出勤天数、请假天数、迟到/早退/缺勤/漏卡次数、扣款额）
  → buildAttendanceData :555-574
       · 实出勤天数 → ATTENDANCE 实发天数类工资项
       · 耗勤扣款额 → ATTENDANCE_DEDUCT 工资项
  → 可被 Excel 导入覆盖（syncAttendanceData=false 时走 parseAttendanceImportData :643）
```

扣款规则：`HrmSalaryAttendanceRuleServiceImpl.calc`（缺勤/事假/病假/满勤奖比例），比例上限由 `HrmSalaryRateLimitService.validateAttendanceRate` 校验（:73-76）。

### 3.2 Insurance → Salary deduct

```
hrm_insurance_month_employee_record
  (personal_insurance_amount, personal_provident_fund_amount,
   corporate_insurance_amount, corporate_provident_fund_amount)
        │
        ├─ 桥服务 HrmSalaryInsuranceDeductServiceImpl :37-84
        │    getDeduct / getDeductListByMonth → 汇总个人社保+公积金
        │
        └─ 核算直读 HrmSalaryMonthRecordServiceImpl :247-252 / :299-308
             · syncInsuranceData=true 时：
                 validateMonthRecordExists（社保月表必须存在） :248
                 getNormalMonthEmployeeRecordMap（按社保年月）
                 缺记录 → 个人社保/公积金按 0，log.warn 留痕 :303-304
             · buildInsuranceOptionValues :578-595
                 个人社保 / 个人公积金 → 税前扣除项
                 单位社保 / 单位公积金 → 单位成本（不进实发）
```

社保年月对齐：`resolveSocialSecurityYearMonth` :809-818，支持 `HrmSalarySocialSecurityMonthTypeEnum` 当月/上月/下月。

### 3.3 Performance → Salary

```
hrm_performance_assessment (processStatus=FINISHED) + hrm_performance_plan (status != TERMINATED)
        │
        ├─ 桥服务 HrmSalaryPerformanceServiceImpl
        │    getLatestPerformance / getLatestPerformanceMap
        │    · 只取 FINISHED 考核 :55-59 / :93-96
        │    · filterTerminatedPlan 剔除已终止计划 :121-132
        │
        └─ 核算应用 applyPerformanceSalary
             HrmSalaryMonthRecordServiceImpl.java:608-635
             · 绩效工资 = 绩效工资基数 × coefficient（无考核时系数=1） :626-628
             · 覆盖 PERFORMANCE_SALARY 工资项
```

按发放月取系数（归档口径）：`HrmSalaryMonthEmployeeRecordServiceImpl.getPerformanceCoefficientMap` :855-863 → `performancePlanService.getPerformancePlanListByPaidForMonth` + `performanceAssessmentService.getPerformanceArchiveEmployeeCoefficientMap`。

### 3.4 Piece records → Salary

```
hrm_salary_piece_record (employeeId, year, month, amount)
        │
        └─ HrmSalaryPieceRecordServiceImpl.getPieceAmountMap :84-94
             · 按 (year, month) 汇总
             → HrmSalaryMonthRecordServiceImpl :230-232 / :321
                 mergePieceworkSalary :753 → 计件工资项（累加或覆盖，取决于 option 配置）
```

### 3.5 汇总公式（payroll 试算镜像）

`HrmSalaryPayrollServiceImpl.calc` :53-130 与月度核算同构：

```
expectedPay = baseSalary + performanceSalary + fullAttendanceBonus + pieceworkSalary
totalDeduct = 考勤扣款 + personalInsurance + personalFund + ...
realPay     = expectedPay - totalDeduct - personalTax（见 computeSalary :428-437）
```

个税三型（`HrmSalaryMonthEmployeeRecordServiceImpl.computeSalary` :412-437）：
- `NONE`：不计税
- `REMUNERATION` 劳务报酬：≤4000 减 800，>4000 减 20%（刻意不用起征点，H-09 注释 :418）
- `SALARY` 工资薪金：应税 − 起征点，走累计预扣 `appendCumulativeSalaryTaxValues` :425

---

## 4. 并发 / 幂等

### 4.1 薪资月度核算

| 机制 | 位置 | 说明 |
|------|------|------|
| 悲观锁 | `HrmSalaryMonthRecordMapper.selectByIdForUpdate` :15；`validateMonthRecordEditableForUpdate` :515-517 | 核算/删除/发工资条前锁月表行 |
| 状态门禁 | `HISTORY` 不可改 :506-517 | 防归档后重算 |
| 最新月约束 | `deleteMonthRecord` 仅最新 :356 | 防乱序删除 |
| 创建侧 | `createMonthRecord` 未见唯一索引强约束（代码层） | 双击创建可能产生重复月表（见风险 R3） |

### 4.2 批量导入（定薪 / 调薪）

`HrmSalaryEmployeeInfoServiceImpl.importFixSalaryList` :275 / `importChangeSalaryList` :308：

- `validateImportRows` :659（空表 → `SALARY_IMPORT_LIST_IS_EMPTY`）
- 按工号 `validateImportEmployee` :665 解析员工
- 整表单事务（类级方法 `@Transactional` :130/:369 局部）；逐行 `createSalaryChangeRecord`
- **无文件级幂等键 / 去重锁**；同一文件重复提交会重复写调薪记录（见 R4）
- 调薪生效校验 `validateSalaryAdjustmentEffectTime` :544；待生效记录冲突 `SALARY_PENDING_CHANGE_RECORD_EXISTS` :74

定时生效：`BE/job/salary/HrmSalaryChangeJob.java`。

### 4.3 工资条发送

`HrmSalarySlipSendRecordServiceImpl.sendSalarySlip` :77：

- 月表 `selectByIdForUpdate` :79（防与核算并发）
- 无「同一月禁止重复发送」的硬锁；`salarySlipSent` 过滤用于查询已发/未发 :149
- 重复调用会新建多条 SendRecord + 多份 Slip（幂等靠业务侧控制，见 R5）
- 站内信在事务内同步发送 :HrmSalarySlipServiceImpl.java:92（失败可能影响主事务）

### 4.4 社保月创建

- `selectLastForUpdate` :59/:79 保证「首月/次月」串行
- `selectByIdForUpdate` :164 保证编辑/删除串行
- 首月重复创建被 `INSURANCE_FIRST_MONTH_RECORD_EXISTS` 拒绝 :59-60（幂等良好）

### 4.5 绩效计划 / 考核

- Plan 更新/启动/归档/终止：`selectOneForUpdate` :139/:233/:249/:270
- Assessment 评分/申诉/阶段处理：`validatePerformanceAssessmentExistsForUpdate` :991 + `assessmentStageMapper.selectOneForUpdate` :519/:591/:631/:1207
- 无分布式锁；多实例靠 DB 行锁

### 4.6 Rate Limit

`HrmSalaryRateLimitServiceImpl`：

- `DEFAULT_RATE_LIMIT = 100.00` :HrmSalaryRateLimitService.java:20
- `validateAttendanceRate` :76-83：超限抛 `SALARY_RATE_LIMIT_EXCEEDED`
- `validateInsuranceSchemeRate` :93-104：单位/个人比例分限
- 可 `isDisabled` 跳过 :112
- 属配置校验，不是 API 限流

### 4.7 其他

- `HrmSalaryOptionServiceImpl.syncSalaryOption` :145-146：`synchronized` JVM 级锁（多实例无效），保护模板同步
- 工号序列：`HrmEmployeeNoSequenceMapper.selectByRuleIdForUpdate`（DB 行锁，跨模块依赖）

---

## 5. Permission 字符串

### 5.1 Attendance

| 权限 | 用途 |
|------|------|
| `hrm:attendance:clock:create` / `update` / `delete` / `query` / `export` | 打卡补录/改/删/查/导出（ClockController :72-135） |
| `hrm:attendance:leave:query` / `export` / `update` | 请假查/导/人工审批（LeaveController :61-92） |
| `bpm:process-instance:query`, `bpm:task:query` | 与 leave:query 组合查流程详情 :61-62 |
| `hrm:attendance:group:create` / `update` / `delete` / `query` | 考勤组 |
| `hrm:attendance:holiday:create` / `update` / `delete` / `query` | 节假日 |
| `hrm:attendance:statistics:query` / `export` | 月度统计 |
| Portal: `hrm:portal:query` | 员工端查打卡/统计 |
| Portal: `hrm:portal:attendance:leave` | 员工端发起/撤销请假 :49-57 |

### 5.2 Salary

| 权限 | 用途 |
|------|------|
| `hrm:salary:month-record:create` / `compute` / `delete` / `query` / `update` | 月表生命周期；`compute` 覆盖核算/同步/导入考勤（MonthRecordController :88-190） |
| `hrm:salary:employee-info:query` / `update` / `import` | 定薪与批量调薪/导入 |
| `hrm:salary:change-record:delete` | 删调薪记录 :ChangeRecordController:67 |
| `hrm:salary:piece-record:update` / `query` | 计件 |
| `hrm:salary:slip:create` / `query` / `update` / `delete` | 工资条 + 发放 + 模板 |
| `hrm:salary:tax-rule:create` / `update` / `delete` / `query` | 计税规则 |
| `hrm:salary:group:create` / `update` / `delete` / `query` | 薪资组 |
| `hrm:salary:option:create` / `update` / `delete` / `query` | 工资项 |
| `hrm:salary:attendance-rule:create` / `update` / `delete` / `query` | 耗勤扣款规则 |
| `hrm:salary:change-template:create` / `update` / `delete` / `query` | 调薪模板 |
| `hrm:salary:rate-limit:query` / `update` | 比例上限 |
| `hrm:salary:config:query` / `update` | 全局薪资配置 |
| Portal: `hrm:portal:query` | 员工端工资条列表/详情/未读汇总 |
| **借用（风险 R6）** | InsuranceDeduct / Payroll / Payment / PerformanceData 控制器统一用 `hrm:salary:attendance-rule:query` |

### 5.3 Insurance

| 权限 | 用途 |
|------|------|
| `hrm:insurance:scheme:create` / `update` / `delete` / `query` | 方案；Standard 复用 `scheme:query` |
| `hrm:insurance:month-record:create` / `delete` / `query` / `update` | 月表 + 员工月记录 |
| `hrm:insurance:employee-info:query` / `update` | 员工参保 |
| Portal: `hrm:portal:query` | 员工端社保记录 |

### 5.4 Performance

| 权限 | 用途 |
|------|------|
| `hrm:performance:plan:create` / `update` / `delete` / `query` | 计划；Assessment 管理端大量复用 `plan:update` / `plan:query` |
| `hrm:performance:assessment-template:create` / `update` / `delete` / `query` | 考核模板 |
| `hrm:performance:result-template:create` / `update` / `delete` / `query` | 结果模板 |
| `hrm:performance:archive:query` / `delete` | 绩效档案 |
| Portal: `hrm:portal:query` | 任务查询 |
| Portal: `hrm:portal:performance:action` | 填指标/评分/确认/申诉/处理 |

---

## 6. Frontend 页面（views/hrm）

### 6.1 attendance

```
FE/attendance/
  clock/
    index.vue, data.ts
    modules/form.vue          # 补录打卡（无班次拦截 :82 / :254）
    modules/daily-detail.vue, overview.vue, record-list.vue
  leave/
    index.vue, data.ts
    AttendanceLeaveProcessDetail.vue
  config/
    group/index.vue, data.ts
    group/modules/form.vue, point-form.vue, shift-form.vue,
             special-date-form.vue, wifi-form.vue
    holiday/index.vue, data.ts, modules/form.vue
  month/
    index.vue, data.ts
    detail/index.vue, detail/data.ts
```

### 6.2 salary

```
FE/salary/
  config/
    config/index.vue
    group/index.vue, data.ts, modules/form.vue
    option/index.vue, modules/form.vue, components/change-option-select.vue
    tax-rule/index.vue, data.ts, modules/form.vue, components/tax-rule-select.vue
    change-template/index.vue, data.ts, modules/form.vue
  employee-info/
    index.vue, data.ts
    modules/form.vue, batch-form.vue, import-form.vue
    components/change-template-select.vue
    detail/index.vue, detail/modules/info-details.vue, change-record-list.vue
  month-record/
    index.vue, data.ts
    modules/compute-form.vue          # 核算前拉 readiness :88-89
    modules/batch-employee-record-form.vue
    components/payroll-readiness-alert.vue      # 「未加入薪资组」告警 :25 / :65
    components/payroll-readiness-employee-list.vue
    detail/index.vue, detail/data.ts, detail/modules/record-details-info.vue
    history/index.vue, history/data.ts
  slip/
    send-record/index.vue, data.ts, modules/slip-send-form.vue
    send-record/detail/index.vue, detail/modules/slip-list.vue, slip-detail.vue
    template/modules/template-form.vue, template-option-editor.vue
    components/salary-option-select.vue
  utils/option.ts
```

API：`API/salary/month-record/index.ts`（`PayrollReadiness` :43，`getSalaryPayrollReadiness` :99 → `GET /hrm/salary/month-record/payroll-readiness`）。

### 6.3 insurance

```
FE/insurance/
  scheme/
    index.vue, data.ts, modules/form.vue
    components/insurance-scheme-select.vue
  month-record/
    index.vue, data.ts
    modules/first-month-form.vue
    detail/index.vue, detail/data.ts
    detail/modules/add-employee-form.vue, batch-employee-record-form.vue,
                 employee-detail-drawer.vue, employee-record-form.vue
```

### 6.4 performance

```
FE/performance/
  plan/
    index.vue, data.ts
    modules/assessment-add-form.vue
    form/index.vue
    form/modules/basic-form.vue, scope-form.vue, indicator-form.vue,
              process-form.vue, handler-stage-form.vue, result-form.vue
    detail/index.vue, detail/modules/header.vue, info.vue
  assessment/
    index.vue, data.ts
    employee/index.vue, detail/index.vue
    components/process-record-timeline.vue
  config/
    assessment-template/index.vue, data.ts, modules/form.vue, dimension-form.vue,
                          components/config-editor.vue, template-select.vue
    result-template/index.vue, data.ts, modules/form.vue, components/level-form.vue
  components/rater-level-select.vue
```

Portal（同模块）：`FE/portal/salary/slip/`、`FE/portal/insurance/record/`、`FE/portal/performance/assessment/`（含 review/process 表单）。

路由：`txgy-ui-admin-vben/apps/web-ele/src/router/routes/modules/hrm.ts`（薪资组 `hideInMenu` :143）。

---

## 7. 代码级风险

| ID | 严重度 | 位置 | 问题 |
|----|--------|------|------|
| R1 | High | `HrmSalaryMonthRecordServiceImpl.java:228-232` | **未入薪资组员工被静默剔除**。`filterList(..., salaryGroupMap.containsKey)` 直接丢弃，不写错误。虽有 `getPayrollReadiness` :391-453 与前端 `payroll-readiness-alert.vue`（「N 名员工未加入任何薪资组，无法参与工资核算」:65），但 readiness 仅提示、**不阻断核算**；漏配薪资组 → 少发工资。 |
| R2 | High | `HrmSalaryMonthEmployeeRecordServiceImpl.java:404-437` | **个税与实发公式分支多**，NONE/REMUNERATION/SALARY 三型扣除顺序不同；劳务报酬注释 H-09 明确「不能复用起征点」。若配置错 `taxRule.type` 或 `threshold`，会系统性少缴/多缴税。建议对 `calculateTax` :576-582、`fillLastMonthCumulativeValues` 做表驱动回归。 |
| R3 | Medium | `HrmSalaryMonthRecordServiceImpl.java:135-156` | **月表创建缺代码层幂等**。无「同年月唯一」校验/唯一索引证据；双击或并发 create 可能产生重复月表，后续 delete/last 语义（仅最新可删 :356）会错乱。社保侧已有 `selectLastForUpdate` 对照。 |
| R4 | Medium | `HrmSalaryEmployeeInfoServiceImpl.java:275-320` | **批量导入无幂等键**。重复上传同一 Excel 会重复生成调薪记录；仅靠 `SALARY_PENDING_CHANGE_RECORD_EXISTS` 防待生效重叠 :74，无法防「已完成调薪的完全重复导入」。 |
| R5 | Medium | `HrmSalarySlipSendRecordServiceImpl.java:77-130` | **工资条发送可重复**。无 per-month 唯一约束；重复点击产生多份 Slip + 多条站内信。事务内同步发消息 :HrmSalarySlipServiceImpl.java:92，通知失败可能拖垮发送事务。 |
| R6 | Medium | `HrmSalaryInsuranceDeductController.java:40-51`、`HrmSalaryPayrollController.java:37`、`HrmSalaryPaymentController.java:37-48`、`HrmSalaryPerformanceController.java:38-47` | **权限串借用**。四个薪资桥/试算/发放接口统一用 `hrm:salary:attendance-rule:query`，权限边界与业务语义不符：拥有耗勤规则查询权即可试算工资、生成银行发放文件、读绩效系数。 |
| R7 | Medium | `HrmSalaryMonthRecordServiceImpl.java:301-308` | **缺社保记录时按 0 扣款**，仅 `log.warn` 留痕。与 R1 叠加：未建社保月记录的员工实发偏高。readiness 会报 `insuranceMissingEmployees` :435-438，但同样不阻断。 |
| R8 | Medium | `HrmSalaryAttendanceRuleServiceImpl.java:70-76` + `HrmSalaryRateLimitServiceImpl.java:76-83` | **比例上限默认 100%**。未配置时 `DEFAULT_RATE_LIMIT=100` 兜底；缺勤扣款比例配成 100 会把应发扣光（合法但极端）。`isDisabled` :112 可整体跳过校验。 |
| R9 | Low-Med | `HrmSalaryOptionServiceImpl.java:145-146` | `syncSalaryOption` 用 JVM `synchronized`，**多实例部署无效**；模板同步并发可能插入重复 option。 |
| R10 | Low-Med | `HrmSalaryMonthEmployeeRecordServiceImpl.java:524 / :576-582` | 金额 `setScale(..., HALF_UP)` 统一四舍五入到 `decimalScale`（默认 2）；累计预扣中途取整可能与税局端累计口径产生分位差。 |
| R11 | Low | `HrmAttendanceLeaveServiceImpl.java:125-134` | 人工审批与 BPM 流程双通道；若 listener 延迟，`approveLeave` 的 RUNNING 判断 :130 可能与回调竞态（无乐观锁版本字段）。 |
| R12 | Low | `HrmInsuranceMonthRecordServiceImpl.java:236` | `archiveMonthRecord` 为 private，仅在 createNext 时自动归档；无显式「归档月」管理端 API，HR 无法单独锁定当月（产品缺口）。 |
| R13 | Low | `HrmPerformanceAssessmentServiceImpl.java:428-484` | 就绪错误码齐全（SCORING/INTERVIEW/ARCHIVE_NOT_READY），但依赖调用顺序；若计划 stageType 与 assessment stage 不同步（启动半失败），可能出现阶段卡死需手工修数。 |
| R14 | Info | `HrmSalaryMonthRecordServiceImpl.java:226` 注释 + :303 | 「按约束不改算钱逻辑，这里只做可观测留痕」——历史约束明确：缺社保/缺组不改金额。QA 回归须把 R1/R7 当**预期行为**验证告警，而非验证阻断。 |

### 7.1 Readiness（发放前检查）覆盖矩阵

`getPayrollReadiness(monthRecordId)` :391-453 输出：

| 检查项 | 字段 | 前端 | 是否阻断核算 |
|--------|------|------|--------------|
| 未分配薪资组 | `noSalaryGroupEmployeeCount/…Employees` | `payroll-readiness-alert.vue:56-65` | 否（静默剔除） |
| 未定薪 | `noSalaryEmployeeCount/…Employees` | 同上 :72-79 | 否 |
| 缺社保月记录 | `insuranceMissingEmployeeCount/…Employees` | alert 组件 | 否（按 0） |
| 调薪/异动计数 | `changeTypeCountMap` | — | 否 |
| 可核算人数 | `payrollEmployeeCount` | `compute-form.vue:88-89` | 否 |

---

## 8. 附录：关键枚举速查

| 枚举 | 值 |
|------|----|
| `HrmSalaryMonthRecordStatusEnum` | 5 UNCOMPUTED / 10 HISTORY / 11 COMPUTED |
| `HrmInsuranceMonthStatusEnum` | 0 UNARCHIVED / 1 ARCHIVED |
| `HrmPerformancePlanStatusEnum` | 1 DRAFT / 2 NOT_STARTED / 3 RUNNING / 4 ARCHIVED / 5 TERMINATED |
| `HrmPerformanceAssessmentProcessStatusEnum` | 1 PROCESSING / 2 FINISHED |
| `HrmPerformanceAssessmentStageStatusEnum` | 0 NOT_PROCESSED / 1 PROCESSED / 2 PENDING / 3 REJECTED / 4 PROCESSING / 5 APPEALED |
| `HrmSalaryChangeRecordStatusEnum` | 0 PENDING / 1 EFFECTIVE / 2 CANCELLED |
| `HrmSalarySlipReadStatusEnum` | UNREAD / READ |
| `HrmInsuranceEmployeeStatusEnum` | 参保/停缴等（月扣款仅取 NORMAL） |
| `HrmSalaryTaxTypeEnum` | NONE / SALARY / REMUNERATION |
| `HrmSalarySocialSecurityMonthTypeEnum` | 当月 / 上月 / 下月 |
| `HrmSalaryTaxCycleTypeEnum` | 自然年 / 12月-次年11月 |
| `HrmSalaryRateLimitBizTypeEnum` | ATTENDANCE_RULE / INSURANCE_SCHEME |

---

*文档仅供 QA / 开发对照，不构成业务代码变更。*
