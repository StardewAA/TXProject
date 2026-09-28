# HRM 员工生命周期 Deep Code Map

> 覆盖范围：org/dept/headcount、company profile、employee（含 contact/education/work/training/file/blacklist/approval/changerecord/quit）、employment type、contract、certificate、safety、policy、no-rule。
>
> 代码根目录：
> - 后端：`txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/`
> - 前端：`txgy-ui-admin-vben/apps/web-ele/src/views/hrm/`（`web-antdv-next` 为并行副本）
>
> 引用格式：`相对路径:行号`。业务代码未改动。

---

## 1. 分层清单：Controller / Service / DO

### 1.1 员工主档与子资源（`controller/admin/employee` + `service/employee/**` + `dal/**/employee/**`）

| 域 | Admin Controller | Service | DO / 表 |
|---|---|---|---|
| 员工主档 | `HrmEmployeeController` (`/hrm/employee`) | `info/HrmEmployeeService(Impl)` | `employee/info/HrmEmployeeDO` → `hrm_employee` |
| 紧急联系人 | `HrmEmployeeContactController` | `info/HrmEmployeeContactService(Impl)` | `HrmEmployeeContactDO` → `hrm_employee_contact` |
| 教育经历 | `HrmEmployeeEducationExperienceController` | `experience/HrmEmployeeEducationExperienceService(Impl)` | `HrmEmployeeEducationExperienceDO` → `hrm_employee_education_experience` |
| 工作经历 | `HrmEmployeeWorkExperienceController` | `experience/HrmEmployeeWorkExperienceService(Impl)` | `HrmEmployeeWorkExperienceDO` → `hrm_employee_work_experience` |
| 培训经历 | `HrmEmployeeTrainingExperienceController` | `experience/HrmEmployeeTrainingExperienceService(Impl)` | `HrmEmployeeTrainingExperienceDO` → `hrm_employee_training_experience` |
| 员工证件（经历侧） | `HrmEmployeeCertificateController` | `experience/HrmEmployeeCertificateService(Impl)` | `HrmEmployeeCertificateDO` → `hrm_employee_certificate` |
| 档案附件 | `HrmEmployeeFileController` | `info/HrmEmployeeFileService(Impl)` | `HrmEmployeeFileDO` → `hrm_employee_file` |
| 入职档案文档 | （`HrmEmployeeController#/onboard-doc/generate`） | `info/HrmEmployeeOnboardDocService(Impl)` | 复用 `hrm_employee_file` |
| 档案完整性卡控 | — | `info/HrmEmployeeArchiveCheckService(Impl)` | 读 `hrm_employee` / `hrm_employee_contract` / `hrm_employee_file` |
| 个人备忘 | `HrmEmployeePersonalNoteController` | `info/HrmEmployeePersonalNoteService(Impl)` | `HrmEmployeePersonalNoteDO` → `hrm_employee_personal_note` |
| 字段显隐配置 | `HrmEmployeeFieldConfigController` | `config/HrmEmployeeFieldConfigService(Impl)` | 配置型（内存/租户配置） |
| 黑名单 | `HrmEmployeeBlacklistController` | `blacklist/HrmEmployeeBlacklistService(Impl)` | `HrmEmployeeBlacklistDO` → `hrm_employee_blacklist` |
| 异动台账 | `HrmEmployeeChangeRecordController` | `employment/HrmEmployeeChangeRecordService(Impl)` | `HrmEmployeeChangeRecordDO` → `hrm_employee_change_record` |
| 员工合同 | `HrmEmployeeContractController` | `employment/HrmEmployeeContractService(Impl)` | `HrmEmployeeContractDO` → `hrm_employee_contract`；文件台账 `HrmEmployeeContractFileDO` → `hrm_employee_contract_file` |
| 工资卡 | `HrmEmployeeSalaryCardController` | `employment/HrmEmployeeSalaryCardService(Impl)` | `HrmEmployeeSalaryCardDO` → `hrm_employee_salary_card` |
| 离职单 | `HrmEmployeeQuitInfoController` | `employment/HrmEmployeeQuitInfoService(Impl)` | `HrmEmployeeQuitInfoDO` → `hrm_employee_quit_info` |
| 离职交接/结算 | `HrmEmployeeQuitHandoverController` | `employment/HrmEmployeeQuitHandoverService(Impl)` | `HrmEmployeeQuitHandoverItemDO` → `hrm_employee_quit_handover_item` |
| 工号规则/序列 | `HrmEmployeeNoRuleController` | `employment/HrmEmployeeNoService(Impl)` | `HrmEmployeeNoRuleDO` → `hrm_employee_no_rule`；`HrmEmployeeNoSequenceDO` → `hrm_employee_no_sequence` |
| 异动审批 | `HrmEmployeeApprovalRecordController` | Controller 内联 + `HrmEmployeeService`/`HrmEmployeeChangeRecordService`/`HrmSalaryEmployeeInfoService` | `HrmEmployeeApprovalDO` → `hrm_employee_approval` |
| 部门编制 | `HrmDeptHeadcountController` | `headcount/HrmDeptHeadcountService(Impl)` | `HrmDeptHeadcountDO` → `hrm_dept_headcount` |

Portal 侧镜像：`controller/admin/portal/employee/*`（`HrmPortalEmployeeController`、Contact/Certificate/Education/Training/Work/QuitInfo/FieldConfig）。

### 1.2 公司 / 用工 / 合同签署 / 证件台账 / 安全 / 制度

| 域 | Admin Controller | Service | DO / 表 |
|---|---|---|---|
| 公司档案 | `company/HrmCompanyProfileController` (`/hrm/company/profile`) | `company/HrmCompanyProfileService(Impl)` | `company/HrmCompanyProfileDO` |
| 用工形式 | `employment/HrmEmploymentTypeController` | `employment/HrmEmploymentTypeService(Impl)` | `employment/HrmEmploymentTypeDO` → `hrm_employment_type` |
| 合同模板 | `contract/HrmContractTemplateController` | `contract/HrmContractTemplateService(Impl)` + `HrmContractRenderService(Impl)` | `HrmContractTemplateDO` |
| 合同签署台账 | `contract/HrmContractSignRecordController` | `contract/HrmContractSignRecordService(Impl)` | `HrmContractSignRecordDO` → `hrm_contract_sign_record` |
| 证件类型 | `certificate/HrmCertificateTypeController` | `certificate/HrmCertificateTypeService(Impl)` | `HrmCertificateTypeDO` |
| 证件台账 | `certificate/HrmCertificateLedgerController` | `certificate/HrmCertificateLedgerService(Impl)` + `HrmCertificateStatusCalculator(Impl)` | 复用 `HrmEmployeeCertificateDO` |
| 证件续期 | （Ledger `#renewal`） | `certificate/HrmCertificateRenewalService(Impl)` | `HrmCertificateRenewalDO` |
| 证件预警 | `certificate/HrmCertificateWarningController` | `certificate/HrmCertificateWarningService(Impl)` | `HrmCertificateWarningDO` |
| 体检 | `safety/HrmHealthCheckController` (`/hrm/health-check`) | `safety/HrmHealthCheckService(Impl)` | `HrmHealthCheckDO` |
| 安全教育 | `safety/HrmSafetyEducationController` (`/hrm/safety-education`) | `safety/HrmSafetyEducationService(Impl)` | `HrmSafetyEducationDO` |
| 制度政策 | `policy/HrmPolicyController` (`/hrm/policy`) | `policy/HrmPolicyService(Impl)` | `HrmPolicyDO` |

### 1.3 关联枚举（状态机源）

- `enums/employee/info/HrmEmployeeEntryStatusEnum.java` — 入职状态（在职/待入职/待离职/离职）
- `enums/employee/info/HrmEmployeeStatusEnum.java` — 用工状态（正式/试用/实习/兼职/劳务/顾问/返聘/外包）
- `enums/employee/employment/HrmEmployeeChangeTypeEnum.java` — 异动类型（转正/调岗/晋升/降级/转全职/再入职/取消离职）
- `enums/employee/employment/HrmEmployeeContractStatusEnum.java` — 合同执行状态
- `enums/employee/employment/HrmEmployeeQuitHandoverStatusEnum.java` — 离职交接状态
- `enums/contract/HrmContractSignStatusEnum.java` — 合同签署状态
- `enums/certificate/HrmCertificateStatusEnum.java` / `HrmCertificateWarningStatusEnum.java`
- `enums/employee/approval/HrmEmployeeApprovalTypeEnum.java`

---

## 2. 状态机（含 file:line）

### 2.1 员工入职状态（在职 / 待入职 / 待离职 / 已离职）

定义：`enums/employee/info/HrmEmployeeEntryStatusEnum.java:21-24`

```
PENDING_ENTRY(2, 待入职)
    │ confirmEmployeeEntry
    ▼
ACTIVE(1, 在职) ⇄ PENDING_LEAVE(3, 待离职)          ACTIVE_STATUSES = {1,3}  :29-30
    │ quitEmployee(plan<=now) 或 applyEmployeeQuit      │ cancelEmployeeQuit
    ▼                                                   │
LEFT(4, 离职) ── rehireEmployee ──► ACTIVE(1)
```

关键迁移：

| 迁移 | 入口 | 证据 |
|---|---|---|
| 待入职 → 在职 | `confirmEmployeeEntry` | `service/employee/info/HrmEmployeeServiceImpl.java:257-272`（要求 `PENDING_ENTRY`，校验完整固定字段） |
| 在职 → 待离职 / 已离职 | `quitEmployee` | `HrmEmployeeServiceImpl.java:636-698`；`planQuitTime<=now` 直接 `LEFT`，否则 `PENDING_LEAVE` |
| 待离职 → 已离职（到点） | `applyEmployeeQuit` | `HrmEmployeeServiceImpl.java:603-629`（由 Job 捡起） |
| 待离职 → 在职 | `cancelEmployeeQuit` | `HrmEmployeeServiceImpl.java:708-743`（删离职单 + 写 CANCEL_QUIT 台账） |
| 已离职 → 在职 | `rehireEmployee` | `HrmEmployeeServiceImpl.java:293-354`（黑名单 + 工资结清卡控） |

前端对齐常量：`txgy-ui-admin-vben/apps/web-ele/src/views/hrm/utils/constants.ts:121-127`。

### 2.2 用工状态与转正（regularize）

定义：`enums/employee/info/HrmEmployeeStatusEnum.java:21-28`（正式=1、试用=2 为 FORMAL；实习/兼职/劳务/顾问/返聘/外包为 INFORMAL）。

```
PROBATION(2) ──regularEmployee / applyEmployeeChange(REGULAR) / applyEmployeeRegular──► REGULAR(1)
INTERN(3) / PART_TIME(4) ──convertEmployeeToFullTime──► PROBATION 或 REGULAR
```

- 转正发起：`HrmEmployeeServiceImpl.java:360-388`
  - 卡档案完整性（手机号/劳动合同/档案附件）`HrmEmployeeServiceImpl.java:363-365` → `HrmEmployeeArchiveCheckServiceImpl.java:42-63`
  - 仅 `PROBATION` 可转正 `:367-369`
  - 创建异动台账并同步 `regularTime` `:376-383`；`effectTime<=now` 立即 `applyEmployeeChange`
- 异动应用（含转正落地）：`applyEmployeeChange` `HrmEmployeeServiceImpl.java:505-564`
  - 行锁读主档 `:517-518`（`selectByIdForUpdate`）
  - REGULAR：`PROBATION → REGULAR` `:525-537`
  - FULL_TIME：仅 `INTERN/PART_TIME` 可转 `:543-559`
- 到期自动转正兜底：`getDueRegularEmployeeList` `:568-583`（跳过 `joinRegular=false` 用工形式）+ `applyEmployeeRegular` `:587-598`
- Job 调度：`job/employee/HrmEmployeeChangeJob.java:48-78`

### 2.3 位置异动（调岗 / 晋升 / 降级 / 转全职 / 再入职 / 取消离职）

类型定义：`enums/employee/employment/HrmEmployeeChangeTypeEnum.java:21-30`；预约生效集合 `PENDING_EFFECT_TYPES={REGULAR,TRANSFER,PROMOTION,DEMOTION,FULL_TIME}` `:38-39`。

| 类型 | 发起 | 立即生效 | 预约生效 |
|---|---|---|---|
| 转正 REGULAR(4) | `regularEmployee` `:360` | `:382-383` | Job `HrmEmployeeChangeJob:48-63` |
| 调岗 TRANSFER(5) | `transferEmployee` `:394-418` | `:408-411` 统一走 `applyEmployeeChange` | 同上 |
| 晋升 PROMOTION(6) | `promoteEmployee` `:424-445` | `:438-440` | 同上 |
| 降级 DEMOTION(7) | `demoteEmployee` `:451-472` | `:465-467` | 同上 |
| 转全职 FULL_TIME(8) | `convertEmployeeToFullTime` `:478-501` | `:495-496` | 同上 |
| 再入职 REHIRE(9) | `rehireEmployee` `:293` | 创建即生效（写台账 `:325-331`） | — |
| 取消离职 CANCEL_QUIT(10) | `cancelEmployeeQuit` `:728-739` | 创建即生效 | — |

台账创建与“未填字段补当前值”：`createEmployeeChangeRecord` `:1841+` / `fillUnchangedPositionFields` `:1886-1910`；台账服务 `HrmEmployeeChangeRecordServiceImpl.java:44-70`（`PENDING_EFFECT_TYPES` 才写 `effectTime` 日初、非预约类型立即写 `appliedTime`）。

### 2.4 离职流程（quit → handover → settle）

交接状态：`enums/employee/employment/HrmEmployeeQuitHandoverStatusEnum.java:22-25`（未开始/进行中/已确认/无需交接）；结论判定 `isFinished` `:51-53`。

```
quitEmployee
  ├─ planQuitTime > now  → PENDING_LEAVE + 离职单
  └─ planQuitTime <= now → 要求交接结论（已确认/无需交接）
        ├─ 已完成：saveEmployeeQuitInfo + LEFT
        └─ 未完成：REQUIRES_NEW 先落离职单 → 抛 EMPLOYEE_QUIT_HANDOVER_REQUIRED
             （见 HrmEmployeeServiceImpl.java:668-686）

交接：
  saveHandoverItem  :58-90   NOT_STARTED → IN_PROGRESS
  deleteHandoverItem :93-108  明细清空回退 NOT_STARTED
  confirmHandover   :168-191  拒绝仍有 PENDING 明细 → CONFIRMED
  waiveHandover     :195-201  → WAIVED
  settleQuitSalary  :217-236  记 salarySettled / cutoffDate
  validateHandoverFinished :204-213
  validateSalarySettled    :239-246  （rehire 卡控）

到点生效 applyEmployeeQuit :603-629
  LEFT + 禁用账号 + terminateEmployeeContracts（交接未完成只 warn，不阻断）
```

文件：`service/employee/employment/HrmEmployeeQuitHandoverServiceImpl.java`、`HrmEmployeeQuitInfoServiceImpl.java:36-59`（独立事务 `REQUIRES_NEW` 用于“先落离职单再交接待办”）。

### 2.5 合同执行状态 + 签署状态

合同执行状态：`enums/employee/employment/HrmEmployeeContractStatusEnum.java:19-22`（未执行0/执行中1/已到期2/已终止3）。

签署状态：`enums/contract/HrmContractSignStatusEnum.java:19-21`（待签0/已签1/已作废2）。

```
签署记录 PENDING ──update/create SIGNED──► 合同 IN_PROGRESS + signTime + expireRemind=true
              └── INVALID / 无 SIGNED 记录 ──► 合同回滚 NOT_PERFORMED（清 signTime）

离职 LEFT ── terminateEmployeeContracts ──► 合同 TERMINATED
到期 ── getExpireWarningList / HrmEmployeeContractExpireJob ──► 站内信预警
```

证据：

- 同合同仅一条待签：`HrmContractSignRecordServiceImpl.java:75-78`
- 幂等补待签：`createPendingSignRecordIfAbsent` `:120-147`
- 签署闭环同步合同：`syncContractSignStatus` `:189-195` → `markContractSigned` `:200-228`（执行中 + 归档签署件到合同文件台账 type=2 `:233-270`）→ `revertContractSigned` `:275-284`
- 合同草稿/续签/绑定 Offer：`HrmEmployeeContractServiceImpl.java:85-427`（`createDraftForOffer` `:293`、`bindEmployeeByOfferId` `:370`、`applyTemplateByEmploymentType` `:406`）
- 到期预警 Job：`job/employee/HrmEmployeeContractExpireJob.java:51-86`（默认 30 天）
- 逾期未签催办 Job：`job/contract/HrmContractSignOverdueJob.java:38-64`（默认 3 天，批量 500）

### 2.6 证件状态与预警

证件状态：`enums/certificate/HrmCertificateStatusEnum.java:21-24`（有效/即将到期/过期/注销）；人工终态 `CANCELLED` 不参与计算 `HrmCertificateStatusCalculatorImpl.java:25-28`。

计算规则：`HrmCertificateStatusCalculatorImpl.java:22-43`
1. CANCELLED 保持
2. 无 `endTime` → VALID（长期有效）
3. `remainDays < 0` → EXPIRED
4. `remainDays <= type.advanceDays` → EXPIRING
5. 否则 VALID

预警状态：`enums/certificate/HrmCertificateWarningStatusEnum.java:19-21`（待处理0/已处理1/已忽略2）。

流转约束：`HrmCertificateWarningServiceImpl.java:117-127`
- 待处理 → 已处理 / 已忽略
- 已处理 / 已忽略 → 仅可回退 PENDING（清空处理痕迹 `:128-131`）

生成幂等键：「证件 + 预警类型 + 预警日期」`createWarningIfAbsent` `:198-218`。

每日 Job 三步：`job/certificate/HrmCertificateWarningJob.java:29-38`
1. `refreshCertificateStatus(today)`
2. `scanAndGenerateWarnings`（到期 + 复审两类 `:84-99`）
3. `notifyPendingWarnings`（失败累计重试，上限 `MAX_NOTIFY_RETRY_COUNT`）

---

## 3. 入职路径：招聘候选人 → 员工（跨域）

入口：`PUT /hrm/recruit/offer/onboard`  
`controller/admin/recruit/HrmRecruitOfferController.java:107-110` → `HrmRecruitOfferServiceImpl.onboard`（校验 `:542`，建档 `:561`，核增 `:566`）

```
Offer(CONFIRMED) + Application + Candidate
        │
        ├─ 幂等：offer.employeeId / application.employeeId 已有则直接返回 :489-493
        ├─ 手机号合法校验 :500-502
        ├─ 黑名单拦截 :504
        ├─ 用工形式 validateUsableForOnboard :506-507（决定合同模板/计薪）
        ├─ 岗位取 entryPostId ?? application.postId :509-525
        │    └─ 需求单可招校验 validateRecruitDemandCanRecruit
        ├─ 工号自动生成（createEmployee 内） :511-512 注释 + HrmEmployeeServiceImpl:754-755
        ├─ clearInvisibleCreateFields :542
        ├─ employeeService.createEmployee :543
        │    ├─ 生成工号 employeeNoService.generateJobNumber
        │    ├─ 绑定/开通后台账号 bindEmployeeUserAccount
        │    └─ createDraftForEmployee 合同草稿
        ├─ offer.employeeId 回写 :544
        ├─ 需求单 increaseOnboardCount（失败不阻断） :563-569
        ├─ generateOnboardDocs 入职档案文档（失败不阻断） :553-559
        ├─ bindEmployeeByOfferId + applyTemplateByEmploymentType :560-563
        ├─ prepareContractForSignature :565 / :579-610
        │    ├─ createPendingSignRecordIfAbsent
        │    ├─ renderDocx + fileApi.createFile + markGeneratedFile
        │    └─ 站内信 CONTRACT_PENDING_SIGN
        └─ application.markOnboarded / linkEmployeeForOnboard :566-570
```

后续确认入职：`confirmEmployeeEntry` `HrmEmployeeServiceImpl.java:257-287`  
→ `recruitCandidateService.confirmRecruitCandidateEntry` `:275-283`（候选人状态回写）。

招聘侧状态回写：`HrmRecruitApplicationServiceImpl.java:331-372`（`markPendingOnboard` / `linkEmployeeForOnboard` / `markOnboarded`）。

---

## 4. 并发与幂等

### 4.1 工号生成

`service/employee/employment/HrmEmployeeNoServiceImpl.java`

| 机制 | 位置 | 说明 |
|---|---|---|
| 行锁取号 | `:55-57` `selectByRuleIdForUpdate` | 序列表行锁 |
| 唯一键冲突兜底 | `:59-72` `DuplicateKeyException` | 首次并发插入唯一键 `uk_hrm_employee_no_sequence(tenant_id,rule_id,period_key,deleted)`（`sql/hrm_202609201427.sql:1005`） |
| 占用跳号 | `:77-86` `MAX_SKIP_COUNT=200` | 含已删除档案，工号永不复用 `:81-82` |
| 按年重置拼年 | `:182-194` | `resetPeriod=1` 时 `periodKey=year` 且工号含年份 |
| 启用规则互斥 | `:139-146` | 启用一条自动停用其它 |
| 禁删启用规则 | `:157-160` | |

员工工号唯一约束（P0 修复）：`sql/2026-09-20-hrm-p0-fix-job-number-unique.sql:37`  
`uk_hrm_employee_job_number(tenant_id, job_number)`。

手工工号禁止：`HrmEmployeeServiceImpl.java:756-758`（`EMPLOYEE_JOB_NUMBER_MANUAL_FORBIDDEN`）；批量建档内存去重 `:821-823`。

### 4.2 其它唯一/幂等

| 点 | 机制 | 证据 |
|---|---|---|
| 员工手机号 / 用户唯一 | `validateMobileUnique` / `validateUserUnique` | `HrmEmployeeServiceImpl.java:1553-1608` |
| 部门编制一部门一条 | `selectByDeptId` 查重 | `HrmDeptHeadcountServiceImpl.java:34-37` |
| 合同待签一合同一条 | `selectByContractIdAndStatus(PENDING)` | `HrmContractSignRecordServiceImpl.java:75-78, :127-131` |
| 证件预警日级幂等 | 「证件+类型+日期」 | `HrmCertificateWarningServiceImpl.java:203-204` |
| 异动生效防重放 | `appliedTime` 非空跳过 + 行锁 | `HrmEmployeeServiceImpl.java:513-521` |
| Offer 入职幂等 | `employeeId` 已有直接返回 | `HrmRecruitOfferServiceImpl.java:489-493` |
| 离职单独立事务 | `Propagation.REQUIRES_NEW` | `HrmEmployeeQuitInfoServiceImpl.java:54-55` |
| 离职结算 | 单离职单字段 `salarySettled`；截止日不得早于申请日 | `HrmEmployeeQuitHandoverServiceImpl.java:217-235` |
| 工资卡历史重复 | 查询按 employeeId 去重保留最新 | `HrmEmployeeSalaryCardServiceImpl.java:112-117` |

### 4.3 定时任务

| Job | Handler | 职责 |
|---|---|---|
| `HrmEmployeeChangeJob` | `hrmEmployeeChangeJob` | 异动生效 + 到期转正 + 到期离职 |
| `HrmEmployeeContractExpireJob` | `hrmContractExpireJob` | 合同到期预警 |
| `HrmContractSignOverdueJob` | `hrmContractSignOverdueJob` | 逾期未签催办 |
| `HrmCertificateWarningJob` | `hrmCertificateWarningJob` | 证件状态重算 + 预警 + 通知 |
| `HrmEmployeeAccountBindJob` | — | 账号绑定扫描 |

---

## 5. Admin 权限字符串

### 5.1 员工域

| 权限 | 用途（示例） |
|---|---|
| `hrm:employee:query` | 列表/详情/子资源查询 |
| `hrm:employee:create` | 建档 / 从用户建档 / 导入预览 |
| `hrm:employee:update` | 更新、转正/调岗/晋升/降级/转全职/离职/取消离职/再入职、子资源写 |
| `hrm:employee:delete` | 删除员工与子资源 |
| `hrm:employee:export` / `hrm:employee:import` | 导出 / 导入 |
| `hrm:employee:config:query` / `hrm:employee:config:update` | 字段显隐配置 |
| `hrm:employee:headcount:create/update/delete/query` | 部门编制 |
| `hrm:employee:blacklist:create/update/delete/query` | 黑名单 |
| `hrm:employee:personal-note:create/delete` | 个人备忘 |
| `hrm:employee:approval:start` | 发起异动审批、查询 |
| `hrm:employee:approval:approve` | 审批通过/驳回 |
| `hrm:employee-no-rule:query/update` | 工号规则 |
| `hrm:contract:expire:query` | 合同到期清单（`HrmEmployeeContractController:58`） |

### 5.2 合同 / 证件 / 公司 / 用工 / 制度 / 安全

| 权限 | 覆盖 |
|---|---|
| `hrm:contract:template:query/create/update/delete/download` | 合同模板（多与 `hrm:employee:*` 做 `hasAnyPermissions` 双授权，见 `HrmContractTemplateController:50-96`） |
| `hrm:contract:sign:create/update/delete/query/urge` | 签署台账 |
| `hrm:certificate:create/update/delete/query` | 证件类型 + 台账 |
| `hrm:certificate:renewal` | 续期 |
| `hrm:certificate:warning:query/handle` | 预警 |
| `hrm:company-profile:query/update` | 公司档案 |
| `hrm:employment-type:create/update/delete/query` | 用工形式 |
| `hrm:policy:create/update/delete/query` | 制度 |
| `hrm:safety:query` | 体检/安全教育查询 |
| `hrm:safety:health:update` / `hrm:safety:education:update` | 体检 / 安全教育维护 |

### 5.3 Portal

- 通用查询：`hrm:portal:query`
- 员工自维护：`hrm:portal:employee:update`
- 部分接口仅 `isAuthenticated()`：`HrmPortalEmployeeCertificateController:38`、`HrmPortalEmployeeTrainingExperienceController:38`（与其它 portal 子资源权限粒度不一致，见风险）

---

## 6. 跨域数据流

### 6.1 employee → contract

1. 建档即草稿：`createEmployee` → `employeeContractService.createDraftForEmployee` `HrmEmployeeServiceImpl.java:776-783`
2. Offer 入职：`createDraftForOffer`（发 Offer 时）→ `bindEmployeeByOfferId` → `applyTemplateByEmploymentType` → `prepareContractForSignature`（`HrmRecruitOfferServiceImpl.java:560-565`）
3. 签署闭环：签署记录 → 合同 `IN_PROGRESS` + 文件台账 `HrmContractSignRecordServiceImpl.java:189-270`
4. 离职闭环：`terminateEmployeeContracts` `HrmEmployeeServiceImpl.java:228+`（`applyEmployeeQuit` / `quitEmployee` 调用）
5. 转正卡控依赖合同：`HrmEmployeeArchiveCheckServiceImpl.java:52-56`

### 6.2 employee → salary card / salary employee info

1. 工资卡（银行卡）：`HrmEmployeeSalaryCardService` 1:1 `selectByEmployeeId`（`HrmEmployeeSalaryCardServiceImpl.java:50-72`）
2. 薪资档案（`hrm_salary_employee_info`，`uk_hrm_salary_employee_info_employee_id`）：
   - 导入建档联动：`saveImportedEmployeeResources` `HrmEmployeeServiceImpl.java:1691-1700`（工资卡 + 社保）
   - 异动审批自动套薪：`applySalaryIfPresent` `HrmEmployeeApprovalRecordController.java:149-178`（基本工资 code 10101 / 绩效 30101）
3. 工资表/发薪侧通过 `employeeId` 读薪资档案与工资卡（薪资模块 `service/salary/**`）

### 6.3 employee → insurance

1. 社保档案 `HrmInsuranceEmployeeInfoService.saveInsuranceEmployeeInfo`：导入建档时写入 `HrmEmployeeServiceImpl.java:1697-1700`
2. 前端员工详情 tab：`views/hrm/employee/detail/modules/insurance-info*.vue`、`insurance-scheme-form.vue`
3. 月度社保记录按员工档案展开（`service/insurance/monthrecord/**`）

### 6.4 quit → handover / settlement

```
quitInfo（计划离职/申请日/结算日）
   ├─ handover_item（工具/劳保/证件/借支…）→ handoverStatus
   ├─ confirmHandover / waiveHandover
   ├─ settleQuitSalary（cutoffDate + salarySettled）
   ├─ rehire 前置 validateSalarySettled
   └─ applyEmployeeQuit 到点：LEFT + 账号禁用 + 合同终止
```

进度聚合：`getHandoverProgress` `HrmEmployeeQuitHandoverServiceImpl.java:111-164`（`closed = handoverFinished && salarySettled` `:157-158`）。

### 6.5 company profile

- 合同渲染签署主体：`HrmEmployeeContractServiceImpl` 依赖 `HrmCompanyProfileService`（`:68`）；签署记录 `resolveEmployerName/Id` `HrmContractSignRecordServiceImpl.java:346-364`。

---

## 7. 前端页面映射（`views/hrm/`）

> 以 `txgy-ui-admin-vben/apps/web-ele` 为准；`web-antdv-next` 结构基本同构。

### 7.1 employee

| 路径 | 职责 |
|---|---|
| `employee/index.vue` + `data.ts` | 员工列表、状态页签、批量操作 |
| `employee/modules/form.vue` | 新建/编辑档案 |
| `employee/modules/create-from-user-form.vue` | 从后台用户建档 |
| `employee/modules/import-form.vue` | Excel 导入 |
| `employee/modules/regular-form.vue` | 转正 |
| `employee/modules/position-change-form.vue` | 调岗/晋升/降级 |
| `employee/modules/full-time-form.vue` | 转为全职 |
| `employee/modules/quit-form.vue` | 离职 |
| `employee/modules/insurance-scheme-form.vue` | 社保方案 |
| `employee/detail/index.vue` | 员工详情壳 |
| `employee/detail/modules/basic-info.vue` / `post-info.vue` | 基本/任职 |
| `.../contact-list|form.vue` | 紧急联系人 |
| `.../education-list|form.vue` | 教育经历 |
| `.../work-list|form.vue` | 工作经历 |
| `.../training-list|form.vue` | 培训经历 |
| `.../certificate-list|form.vue` | 员工证件 |
| `.../contract-list|form.vue` | 员工合同 |
| `.../salary-card-info|form.vue` / `salary-info.vue` / `salary-social-security.vue` | 工资卡/薪资/社保 |
| `.../insurance-info.vue|form.vue` | 社保档案 |
| `.../quit-info.vue` | 离职单/交接/结算 |
| `.../change-record-list.vue` | 异动台账 |
| `.../material-files.vue` | 档案附件 |
| `employee/config/**` | 创建/归档字段显隐配置 |
| `employee/components/employee-select*.vue` | 员工选择器 |

状态常量：`utils/constants.ts:121-236`（EntryStatus / Status / StatusTab / TodoType / SurveyType）。

### 7.2 contract / certificate

- **无独立顶级目录**。合同与证件以员工详情子页签存在：
  - `employee/detail/modules/contract-list.vue` / `contract-form.vue`
  - `employee/detail/modules/certificate-list.vue` / `certificate-form.vue`
- 合同签署台账 / 合同模板 / 证件台账 / 证件预警：**前端页面缺失**（仅有后端 Controller），见风险 8.4。

### 7.3 dept / company / safety / policy

| 域 | 前端 |
|---|---|
| dept | `dept/index.vue`、`dept/detail/**`（部门详情 + 员工列表）；编制 headcount 无独立页 |
| company | **无** `views/hrm/company` |
| safety | **无** `views/hrm/safety` |
| policy | **无** `views/hrm/policy` |

### 7.4 其它相关

- 招聘候选人转员工入口：`recruit/candidate/**`
- 员工端 Portal：`portal/employee/**`
- 首页待办/概况：`home/components/employee-survey.vue`、`todo-survey.vue`

---

## 8. 代码级风险 / 缺口

### 8.1 并发与数据一致性

1. **`applyEmployeeChange` 与 `applyEmployeeRegular` 双路径转正**  
   `applyEmployeeChange` `:505` 用行锁；`applyEmployeeRegular` `:587` **无行锁**，Job 与审批回调并发时可能重复更新/丢失更新。审批通过走 `applyEmployeeRegular`（`HrmEmployeeApprovalRecordController.java:121-123`），与异动台账路径不一致。

2. **`quitEmployee` 独立事务与 REPEATABLE READ 快照**  
   `:673-685` 注释已承认：REQUIRES_NEW 落库后本事务读不到新行，以“本次提交前”的离职单判断交接。极端时序下交接结论判断可能过期（设计取舍，需监控）。

3. **`settleQuitSalary` 幂等弱**  
   `:217-236` 可重复调用覆盖 `salarySettleRemark`/`cutoffDate`，无版本号或状态机禁止二次结算。

4. **工号序列 `updateById` 无条件更新**  
   `HrmEmployeeNoServiceImpl.java:78-79` 在 FOR UPDATE 行锁内，但若 sequence 行被删重建，理论上有 ABA；当前靠唯一键 + 跳号兜底。

### 8.2 状态机与业务闭环

5. **到点离职不阻断未完成交接**  
   `applyEmployeeQuit:619-624` 只 `log.warn`。人已 LEFT、物品未交接的风险窗口存在（与手工立即离职卡控 `:668-686` 口径不一致）。

6. **合同“已到期”状态未见自动落库**  
   `HrmEmployeeContractStatusEnum.EXPIRED` 有枚举，但 `HrmEmployeeContractExpireJob` 只发预警不改 status；`getExpireWarningList` 按 `end_time` 扫描。状态字段可能长期停在「执行中」。

7. **证件 CANCELLED 后仍可被扫描源影响？**  
   `calculateStatus` 保护 CANCELLED（`HrmCertificateStatusCalculatorImpl:25-28`），但 `scanAndGenerateWarnings` 依赖 `selectListForWarningScan` 过滤“未注销”；需确认 Mapper 过滤完整（服务层二次校验缺失）。

8. **审批类型覆盖不全**  
   `HrmEmployeeApprovalTypeEnum` 落地只处理 REGULAR / TRANSFER / QUIT（`HrmEmployeeApprovalRecordController:121-134`）；晋升/降级/转全职若走审批单会“已批未生效”。

### 8.3 权限与安全

9. **Portal 权限粒度不一致**  
   `HrmPortalEmployeeCertificateController:38`、`HrmPortalEmployeeTrainingExperienceController:38` 仅 `isAuthenticated()`，而 Contact/Education/Work 用 `hrm:portal:query`。跨员工 ID 越权依赖服务层 `validateEmployeeBySelf`，需确认子资源写接口均校验归属。

10. **合同模板双授权面过宽**  
    `HrmContractTemplateController:71-87` 的 create/update/delete 同时接受 `hrm:employee:update`。拥有员工编辑权即可改合同模板，权限边界偏宽。

### 8.4 前后端覆盖缺口

11. **后端有、前端无独立页面**：  
    合同签署台账、合同模板、证件台账/预警/续期、公司档案、体检、安全教育、制度政策、部门编制、黑名单、工号规则、异动审批。  
    影响：QA 需用 API 验收；产品功能不可达或藏在未注册菜单中。

12. **前端 `constants.ts` 存在多套“状态”数字域**  
    `HrmEmployeeEntryStatus`(1-4) 与 `HrmEmployeeStatusTab`(11-15)、`HrmEmployeeTodoType`/`SurveyType` 并存（`constants.ts:121-236`）。易混用导致筛选条件错误。

### 8.5 工程结构

13. **审批逻辑写在 Controller**  
    `HrmEmployeeApprovalRecordController` 内含事务与薪资联动（`:108-188`），不利于复用与单测；与 `service/employee` 分层不一致。

14. **导入路径工号可手工**  
    `createEmployee` 禁手工工号（`:756-758`），但 `importEmployeeList` / `buildImportedEmployee` 走独立逻辑，口径需单独 QA。

15. **`web-ele` 与 `web-antdv-next` 双份 views**  
    改一处漏一处的漂移风险。

---

## 9. 快速跳转索引

| 主题 | 主文件 |
|---|---|
| 员工生命周期编排 | `service/employee/info/HrmEmployeeServiceImpl.java` |
| 异动应用 | 同上 `applyEmployeeChange:505` |
| 离职交接结算 | `service/employee/employment/HrmEmployeeQuitHandoverServiceImpl.java` |
| 工号 | `service/employee/employment/HrmEmployeeNoServiceImpl.java` |
| 合同签署闭环 | `service/contract/HrmContractSignRecordServiceImpl.java` |
| 证件预警 | `service/certificate/HrmCertificateWarningServiceImpl.java` + `HrmCertificateStatusCalculatorImpl.java` |
| Offer 入职 | `service/recruit/application/HrmRecruitOfferServiceImpl.java:487` |
| 异动审批 | `controller/admin/employee/HrmEmployeeApprovalRecordController.java` |
| 档案卡控 | `service/employee/info/HrmEmployeeArchiveCheckServiceImpl.java` |
| 前端员工 | `txgy-ui-admin-vben/apps/web-ele/src/views/hrm/employee/**` |
| 唯一约束 SQL | `sql/2026-09-20-hrm-p0-fix-job-number-unique.sql`、`sql/hrm_202609201427.sql:1005` |
