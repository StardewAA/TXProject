# HRM Code Map — Portal / Employee Side + Security / Data-Permission + Jobs

> Scope: `txgy-module-hrm` (controllers under `controller/admin/portal/**` and `controller/admin/recruit/**`), `framework/security`, `framework/datapermission`, `job/**`, frontend `views/hrm/portal/**` and public recruit `views/portal/recruit/**`.
> Method: static code map from source. No business code was modified.
> Generated for QA / security review. Paths are repo-relative unless noted.

---

## 1. HrmPortal* controllers and HrmRecruitAgent* (admin + portal)

All portal controllers live under `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/portal/`.

### 1.1 Employee self-service (require employee binding)

| Controller | Base path | Endpoints | Auth / isolation |
|---|---|---|---|
| `home/HrmPortalHomeController` | `/hrm/portal/home` | `GET /calendar` | `hrm:portal:query`; `getLoginUserId()` |
| `employee/HrmPortalEmployeeController` | `/hrm/portal/employee` | `GET /get-bind-status`, `GET /get`, `PUT /update` | query / `hrm:portal:employee:update`; `validateEmployeeBySelf` |
| `employee/HrmPortalEmployeeContactController` | `.../contact` | `GET /list` | `hrm:portal:query`; self `employeeId` |
| `employee/HrmPortalEmployeeCertificateController` | `.../certificate` | `GET /list` | **`isAuthenticated()` only** |
| `employee/HrmPortalEmployeeEducationExperienceController` | `.../education-experience` | `GET /list` | `hrm:portal:query` |
| `employee/HrmPortalEmployeeTrainingExperienceController` | `.../training-experience` | `GET /list` | **`isAuthenticated()` only** |
| `employee/HrmPortalEmployeeWorkExperienceController` | `.../work-experience` | `GET /list` | `hrm:portal:query` |
| `employee/HrmPortalEmployeeQuitInfoController` | `.../quit-info` | `GET /get` | `hrm:portal:query` |
| `employee/HrmPortalEmployeeFieldConfigController` | `.../field-config` | `GET /list` | `hrm:portal:query` |
| `attendance/HrmPortalAttendanceClockController` | `/hrm/portal/attendance/clock` | `GET /get-detail`, `POST /create`, `GET /list` | `hrm:portal:query` |
| `attendance/HrmPortalAttendanceLeaveController` | `/hrm/portal/attendance/leave` | `GET /list`, `POST /create`, `PUT /cancel` | query / `hrm:portal:attendance:leave` |
| `attendance/HrmPortalAttendanceStatisticsController` | `/hrm/portal/attendance/statistics` | `GET /month-detail`, `GET /export-excel` | `hrm:portal:query` |
| `insurance/HrmPortalInsuranceController` | `/hrm/portal/insurance/record` | `GET /list`, `GET /get` | `hrm:portal:query`; detail checks `record.employeeId == self` (`HrmPortalInsuranceController.java:70-75`) |
| `salary/HrmPortalSalarySlipController` | `/hrm/portal/salary/slip` | `GET /unread-summary`, `GET /list`, `GET /get`, `PUT /read` | `hrm:portal:query`; always `validateEmployeeBySelf` + `getSalarySlipByIdAndEmployeeId` |
| `performance/HrmPortalPerformanceAssessmentController` | `/hrm/portal/performance/assessment` | page + 7 task pages + `get` + `process-record-list`; mutations: `fill-quota`, `confirm-target`, `score-preview`, `score`, `reject-review-stage`, `handle-result-audit`, `confirm-result`, `submit-appeal`, `handle-appeal` | query vs `hrm:portal:performance:action` |

### 1.2 Public recruit / candidate portal (cross-tenant by design)

| Controller | Base path | Endpoints | Notes |
|---|---|---|---|
| `recruit/HrmPortalRecruitController` | `/hrm/portal/recruit` | `GET /post/page`, `/post-type/list`, `/post/get`, `/company/list`, `/company/get`, `/company/post/page` marked **`@PermitAll`** (anonymous browse, `@TenantIgnore`); `POST /application/create`, `GET /application/page`, `GET /application/get`, `PUT /application/withdraw` | Application APIs use `getLoginUserId()` + `createPortalApplication*` / `getPortalApplication*` / `withdrawPortalApplication` |
| `recruit/HrmPortalRecruitChatController` | `/hrm/portal/recruit/chat` | `POST /conversation/open`, `GET /conversation/list`, `GET /message/list`, `POST /message/send`, `PUT /conversation/read`, `PUT /conversation/end` | Class-level `@PreAuthorize("isAuthenticated()")` |
| `recruit/HrmPortalRecruitOfferController` | `/hrm/portal/recruit/offer` | `PUT /accept`, `PUT /reject` | Class-level `isAuthenticated()` |
| `resume/HrmPortalResumeController` | `/hrm/portal/resume` | `POST /upload`, `/upload-generated`, `/create`, `GET /list`, `DELETE /delete` | `@TenantIgnore`; **no `@PreAuthorize`** |
| `resume/HrmPortalResumeVersionController` | `/hrm/portal/resume-version` | `POST /create`, `GET /list`, `GET /get` | `@TenantIgnore`; **no `@PreAuthorize`** |
| `agent/HrmPortalRecruitAgentController` | `/hrm/portal/recruit-agent` | `POST /resume/analyze`, `/job/match`, `/application/precheck`, `/resume/optimize`, `/resume/optimize/apply`, `GET /task/get` | `@TenantIgnore` + `@DataPermission(enable=false)`; **no `@PreAuthorize`** |

### 1.3 Admin HrmRecruitAgent* (`controller/admin/recruit/agent/`)

| Class | Base path | Endpoints | Permission |
|---|---|---|---|
| `HrmRecruitAgentController` | `/hrm/recruit/agent` | `POST /candidate/summary`, `POST /application/match`, `POST /application/interview-question`, `GET /task/get` | `hrm:recruit:agent:candidate-summary`, `:application-match`, `:interview-question`, `:audit-query` |
| Supporting services | — | `HrmRecruitAgentAdminService(Impl)`, `HrmRecruitAgentAnalyzeService(Impl)`, `HrmRecruitAgentTaskService(Impl)`, `HrmRecruitAgentSceneRunner`, `HrmResumeOptimizeApplyService` | Task ownership differs: portal uses `getTask(userId, taskId)`; admin uses `getTaskInTenant(taskId)` |

Related admin recruit controllers (for contrast with portal):
`HrmRecruitApplicationController`, `HrmRecruitCandidateController`, `HrmRecruitResumeController`, `HrmRecruitOfferController`, `HrmRecruitInterviewController`, `HrmRecruitPostController`, `HrmRecruitPostTypeController`, `HrmRecruitChannelController`, `HrmRecruitDemandController`, `HrmRecruitConfigController`, `HrmRecruitCompanyProfileController`, `HrmAdminRecruitChatController` — all use fine-grained `hrm:recruit:*` permissions.

---

## 2. Security architecture: framework/security, framework/datapermission, tenant vs user isolation

### 2.1 Module security config

`framework/security/config/SecurityConfiguration.java:16-33` registers `hrmAuthorizeRequestsCustomizer` with **permitAll** on:

- Swagger: `/v3/api-docs/**`, `/webjars/**`, `/swagger-ui`, `/swagger-ui/**`
- Actuator: `/actuator`, `/actuator/**`
- Druid: `/druid/**`
- Internal RPC: `ApiConstants.PREFIX + "/**"` (typically `/rpc/**` — expected to be network-isolated)

Default fallthrough (framework `TxgyWebSecurityConfigurerAdapter`) is **authenticated** unless `@PermitAll` / configured permitAll.

`framework/web/config/HrmWebConfiguration` — MVC/web glue only.

### 2.2 Data permission (department / self)

`framework/datapermission/config/HrmDataPermissionConfiguration.java:58-94` registers `DeptDataPermissionRuleCustomizer`:

**Registered (dept-scoped, self-scoped for employee master):**

- Employee master: `HrmEmployeeDO` dept + `user_id` (`:61-62`)
- Recruit demand / headcount: `HrmRecruitDemandDO`, `HrmDeptHeadcountDO` (`:64-65`)
- Attendance: clock / leave (`:67-68`)
- Performance: assessment + action + appeal records (`:70-72`)
- Contract / certificate / safety / health (`:74-80`)
- Change / quit / quit handover / employee approval (`:82-85`)
- Employee sub-archives: contact / education / work / training / file / personal note / salary card (`:87-93`)

**Deliberately NOT registered** (documented at `:48-50`):

- Salary (`hrm_salary_*`)
- Insurance (`hrm_insurance_*`)
- Recruit (`hrm_recruit_*` including post/candidate/application/interview/offer/chat)
- Personal resume (`hrm_personal_resume*`)
- Employee blacklist (company-wide risk list)

Role data-range semantics (from config comments `:36-42`):

| Role data range | SQL effect |
|---|---|
| All (HR / Chairman) | no extra filter |
| Dept / custom depts | `dept_id IN (...)` |
| Self only | `user_id = current user` (employee master) |
| No login (jobs / anonymous) | **no extra filter** |

Portal controllers commonly annotate `@DataPermission(enable = false)` (insurance, offer, agent) and instead force identity via `employeeService.validateEmployeeBySelf(getLoginUserId())` or service methods that take `userId` from session.

### 2.3 Tenant isolation: portal vs admin

| Surface | Tenant behavior | Evidence |
|---|---|---|
| Admin HRM APIs | MyBatis tenant plugin filters by current login tenant | standard; agent `getTaskInTenant` double-checks (`HrmRecruitAgentTaskServiceImpl.java:137-149`) |
| Employee self-service portal | same tenant as login user; self via `user_id` / `employeeId` | `validateEmployeeBySelf` |
| Public recruit browse | **`@TenantIgnore`** + `TenantUtils.executeIgnore` — multi-tenant job board | `HrmPortalRecruitController.java:88-210` |
| Portal apply / applications | re-enters post tenant via `TenantUtils.execute(post.getTenantId(), ...)` | `HrmRecruitApplicationServiceImpl.java:396-405` |
| Portal personal resume / resume-version / recruit-agent | class-level `@TenantIgnore` — user-owned, not tenant-owned | controllers above |
| Portal offer accept | loads offer across tenants then `TenantUtils.execute(application.getTenantId(), ...)` | `HrmRecruitOfferServiceImpl.java:429-440, 627-636` |
| Jobs | `@TenantJob` iterates tenants | all `job/**` handlers |

### 2.4 Resume privacy / masking

| Component | Role |
|---|---|
| `util/HrmSensitiveTextMasker.java` | Regex masker for free text: mobile (`:21`), email (`:24`), 18/15-digit ID (`:27,:30`), detailed address (`:41-45`), sensitive labels (gender/age/marriage/etc. `:32-33`). `mask()` `:53-64`, `maskResumeText()` `:67-70`, `stillContainsSensitive()` fail-closed `:78-85`. Keep-patterns: mobile 3+4 `:89-97`, email first-char+domain `:101-112`, ID 6+4 `:116-124`, address to district `:128-139`. |
| `util/HrmResumeAgentPayloadUtils` | Builds AI payloads; applies `maskResumeText`, field-level mask for mobile/phone/email/idCard (`:248-252`); throws `SensitiveMaskFailedException` if residual sensitive data (`:111-122, :164, :258-259`). |
| `framework/agent/core/HrmResumeFactGuard` | Prevents AI from inventing employers/dates/degrees/certs. |
| `HrmRecruitAgentSceneRunner` | Catches `SensitiveMaskFailedException` (`:83`) — fail closed, no model call. |
| Portal application VO | `HrmPortalRecruitApplicationRespVO.mobileMasked` + local `maskMobile` (`HrmPortalRecruitController.java:283, :374`). |

Masking is applied **before LLM egress**, not as a general API response filter. Admin candidate/resume endpoints return full PII to users with `hrm:recruit:candidate:query` (by design).

---

## 3. Permission strings and @PreAuthorize patterns

### 3.1 Portal permission strings (menu SQL: `sql/hrm_menu.sql:150-164`)

| Permission | Used for |
|---|---|
| `hrm:portal:query` | Almost all portal reads (home, employee get, attendance, insurance, salary, performance pages, field config, experiences) |
| `hrm:portal:employee:update` | `PUT /hrm/portal/employee/update` |
| `hrm:portal:attendance:leave` | leave create/cancel |
| `hrm:portal:performance:action` | performance mutations (fill-quota, score, appeal, etc.) |

### 3.2 Admin recruit-agent permissions (`sql/2026-09-17-hrm-recruit-agent-permissions.sql:14-17`)

- `hrm:recruit:agent:candidate-summary`
- `hrm:recruit:agent:application-match`
- `hrm:recruit:agent:interview-question`
- `hrm:recruit:agent:audit-query`

### 3.3 Pattern classes

1. **Fine-grained `@ss.hasPermission('hrm:recruit:...')`** — all admin recruit CRUD/screen/offer/interview/chat/agent.
2. **Coarse `hrm:portal:query`** — most employee self-service reads (one string gates home + salary + insurance + attendance + performance pages).
3. **`isAuthenticated()`** — recruit chat/offer, certificate list, training-experience list.
4. **No annotation** — resume, resume-version, portal recruit-agent, and portal recruit application mutations.
5. **`@PermitAll`** — public job/company browse only.

### 3.4 Endpoints missing or weak checks

| Endpoint group | Gap | Evidence |
|---|---|---|
| `HrmPortalRecruitController` `POST /application/create`, `GET /application/page`, `GET /application/get`, `PUT /application/withdraw` | No `@PreAuthorize` / no `@PermitAll`; relies on default authenticated + service owner check | `HrmPortalRecruitController.java:215-239` |
| `HrmPortalResumeController` entire surface | No `@PreAuthorize`; only `getLoginUserId()` | `HrmPortalResumeController.java:41-69` |
| `HrmPortalResumeVersionController` entire surface | No `@PreAuthorize` | `:45-67` |
| `HrmPortalRecruitAgentController` entire surface | No `@PreAuthorize`; AI tasks for any authenticated user | `HrmPortalRecruitAgentController.java:47-85` |
| `HrmPortalRecruitOfferController` accept/reject | Only `isAuthenticated()` (not `hrm:portal:query`) | `HrmPortalRecruitOfferController.java:23` |
| `HrmPortalRecruitChatController` all methods | Only `isAuthenticated()` | `HrmPortalRecruitChatController.java:35` |
| `HrmPortalEmployeeCertificateController` list | `isAuthenticated()` instead of `hrm:portal:query` | `HrmPortalEmployeeCertificateController.java:38` |
| `HrmPortalEmployeeTrainingExperienceController` list | same | `:38` |
| Admin `GET /hrm/recruit/agent/task/get` | Permission OK but **no per-user ownership** — any tenant user with `audit-query` can read any task in tenant | `HrmRecruitAgentController.java:66-72` → `getTaskInTenant` |
| Module SecurityConfiguration | `/actuator/**` and `/druid/**` permitAll | `SecurityConfiguration.java:28-31` |

---

## 4. Jobs under `job/**` — schedules and side effects

All handlers use `@XxlJob` + `@TenantJob` (per-tenant execution). Cron lives in XXL-Job admin, not in code.

| Job | Handler | Suggested cadence (Javadoc) | Side effects |
|---|---|---|---|
| `certificate/HrmCertificateWarningJob` | `hrmCertificateWarningJob` | daily | `refreshCertificateStatus`, create warnings, `notifyPendingWarnings` |
| `contract/HrmContractSignOverdueJob` | `hrmContractSignOverdueJob` | daily / param `overdueDays` (default 1, min 1) | `urgeSignRecords` for pending signs older than deadline |
| `employee/HrmEmployeeAccountBindJob` | `hrmEmployeeAccountBindJob` | daily dawn | `employeeService.autoBindUserAccount()` binds system users to employee records |
| `employee/HrmEmployeeChangeJob` | `hrmEmployeeChangeJob` | daily / optional date param | Applies due change records (`applyEmployeeChange`), auto-regularize probation (`applyEmployeeRegular`), apply due quits (`applyEmployeeQuit`) |
| `employee/HrmEmployeeContractExpireJob` | `hrmContractExpireJob` | daily / `warnDays` | Sends notify template `hrm_contract_expire_warning` to employee user |
| `performance/HrmPerformanceAppealTimeoutJob` | `hrmPerformanceAppealTimeoutJob` | frequent | Auto-handles timed-out appeal stages (`processAppealTimeout`) |
| `recruit/HrmRecruitChatTimeoutJob` | `hrmRecruitChatTimeoutJob` | frequent | `expireWaitingConversations` closes stale chats |
| `recruit/HrmRecruitInterviewReminderJob` | `hrmInterviewReminderJob` | hourly / `remindHours` (default 24) | Notify interviewers + mail candidates |
| `recruit/HrmRecruitOfferExpireJob` | `hrmRecruitOfferExpireJob` | hourly | `offerService.expireOverdueOffers(null)` — expires all overdue offers |
| `salary/HrmSalaryChangeJob` | `hrmSalaryChangeJob` | daily | `applyDueSalaryChanges(targetDate)` mutates salary employee info |

**Cross-cutting job risks:** jobs run with no login user → data-permission adds no filter (documented `HrmDataPermissionConfiguration.java:42`). Correctness depends on tenant iteration + explicit service filters. `HrmRecruitOfferExpireJob` is also invoked lazily from portal reads (`expireOverdueOffers(applicantUserId)` inside application/offer paths).

---

## 5. Frontend portal pages

### 5.1 Employee portal — `txgy-ui-admin-vben/apps/*/src/views/hrm/portal/**` (mirrored in web-antd, web-ele, web-antdv-next) and uniapp `txgy-ui-admin-uniapp-master/src/pages-hrm/portal/**`

| Area | Files | Backend |
|---|---|---|
| Home | `home/index.vue`, `home/components/employee-survey.vue` | `/hrm/portal/home` |
| Employee archive | `employee/index.vue`, `EmployeeBaseInfo.vue`, `EmployeeForm.vue`, `EmployeePostInfo.vue` | `/hrm/portal/employee*` |
| Attendance | `attendance/report/index.vue`, `AttendanceCalendar.vue`, `AttendanceLeaveList.vue`, `attendance/leave/AttendanceLeaveForm.vue` | `/hrm/portal/attendance/*` |
| Insurance | `insurance/record/index.vue`, `InsuranceRecordDetail.vue` | `/hrm/portal/insurance/record` |
| Salary slip | `salary/slip/index.vue` | `/hrm/portal/salary/slip` |
| Performance | `performance/assessment/**` (index, history, detail, review/process forms, PerformanceTaskTable/Tabs) | `/hrm/portal/performance/assessment` |
| Opening guide | `opening-guide/index.vue` | static / employee |

Frontend access codes mirror backend: `hrm:portal:employee:update`, `hrm:portal:attendance:leave`, `hrm:portal:performance:action` (`hasAccessByCodes` in `EmployeeBaseInfo.vue:213`, `attendance/report/index.vue:377`, `PerformanceTaskTable.vue` multiple).

### 5.2 Public recruit portal — `txgy-ui-admin-vben/apps/web-antd/src/views/portal/recruit/**` (separate from hrm/portal)

| File | Purpose |
|---|---|
| `index.vue`, `detail.vue`, `company.vue` | Job board / post detail / company directory |
| `messages.vue` | Recruit chat |
| `online-resume.vue`, `profile-setup.vue` | Personal resume |
| `components/apply-modal.vue` | Apply |
| `components/resume-ai-panel.vue`, `pre-submit-check.vue`, `resume-tools*.ts/vue`, `resume-optimize-decisions.ts` | Portal recruit-agent UI |
| `profile/recruit.vue` (under `views/portal/profile`) | Candidate profile |

API client: `apps/web-antd/src/api/hrm/portal/recruit-agent/index.ts` hits `/hrm/portal/recruit-agent/*`.

Menu registration for recruit apply page: `sql/2026-09-03-hrm-recruit-groups-integration.sql:113` → component `portal/recruit/index`, permission `hrm:portal:query`.

---

## 6. Data flow: employee self-service vs HR admin; offer accept from portal

### 6.1 Employee self-service

```
Login user
  → SecurityFrameworkUtils.getLoginUserId()
  → employeeService.validateEmployeeBySelf(userId)   // binds user → employee
  → service query/mutate filtered by employeeId (or userId)
```

Typical call sites: `HrmPortalEmployeeController.java:63`, `HrmPortalSalarySlipController.java:59,77,94,106`, `HrmPortalInsuranceController.java:58,71`, `HrmPortalAttendance*`, sub-archive list controllers.

`updateEmployeeBySelf(getLoginUserId(), reqVO)` (`HrmPortalEmployeeController.java:96`) restricts editable fields (driven by `field-config` + `hrm:portal:employee:update`).

### 6.2 HR admin

```
Login HR (tenant-scoped)
  → @PreAuthorize('hrm:xxx:query|create|update|...')
  → MyBatis tenant filter + optional DeptDataPermission
  → full CRUD (employee, recruit pipeline, salary, insurance, performance plans…)
```

Admin recruit pipeline: application screen → interview → offer create/submit/approve/send → confirm → onboard (`HrmRecruitApplicationController`, `HrmRecruitOfferController`).

### 6.3 Offer accept from portal

```
PUT /hrm/portal/recruit/offer/accept?id=...
  HrmPortalRecruitOfferController.accept (isAuthenticated)
    → offerService.acceptByCandidate(id, getLoginUserId())
        1. expireOverdueOffers(userId)                    // side effect
        2. getCandidateOfferApplication(id, userId)       // ownership: applicantUserId must match
             TenantUtils.executeIgnore(offerMapper.selectById)
             TenantUtils.executeIgnore(applicationService.getApplication)
             require userId == application.applicantUserId && tenantId != null   // :627-636
        3. TenantUtils.execute(application.tenantId):
             requiredForUpdate(id)                       // pessimistic lock
             re-check applicantUserId + approvalStatus == SENT   // :433-438
             confirm(id)                                 // transitions offer + application
```

Reject is symmetric (`rejectByCandidate` `:444-454`) and requires a non-blank reason.

Application creation flow (`createPortalApplication` `:396-495`):

1. Resolve post across tenants.
2. `TenantUtils.execute(post.tenantId, createPortalApplicationInTenant)`.
3. `chatService.hasApplyQualification(userId, postId)` gate.
4. Load owned resume version / personal resume by `userId`.
5. Upsert candidate; create application with `applicantUserId`.
6. `markApplicationSubmitted`.

Reads of applications always go through `selectByIdAndApplicantUserId` / `selectPageByApplicantUserId` (`:501-515`).

---

## 7. Auth / isolation risks (IDOR, missing tenant filter, weak resume access)

Ordered by severity. All citations are `file:line`.

### R1 — Portal resume / resume-version / recruit-agent endpoints lack permission annotations
- `HrmPortalResumeController.java:41-69` — upload/create/list/delete with no `@PreAuthorize`.
- `HrmPortalResumeVersionController.java:45-67` — same.
- `HrmPortalRecruitAgentController.java:47-85` — same; also `@TenantIgnore` + `@DataPermission(enable=false)` (`:34-38`).
- **Impact:** any authenticated principal (not only users with `hrm:portal:*`) can create/delete resumes and trigger LLM jobs. Ownership is enforced in service (`HrmPersonalResumeServiceImpl.java:104-124` via `selectByIdAndUserId`), so classic IDOR is mitigated, but authorization is under-gated and cost/abuse surface is open.

### R2 — Portal recruit application mutations have no PreAuthorize
- `HrmPortalRecruitController.java:215-239` — `application/create|page|get|withdraw` have neither `@PreAuthorize` nor `@PermitAll` (unlike browse APIs at `:88,:98,:144,:152,:197,:206`).
- **Impact:** relies entirely on global authenticated default + `getPortalApplication(..., applicantUserId)` owner filter (`HrmRecruitApplicationServiceImpl.java:512-515`). Acceptable if default security holds; brittle if security filter chain is customized or RPC is exposed.

### R3 — Admin agent `task/get` is tenant-scoped but not user-scoped (in-tenant IDOR)
- `HrmRecruitAgentController.java:66-72` calls `agentService.getTaskResult(taskId)`.
- `HrmRecruitAgentAdminServiceImpl.java:122-123` → `taskService.getTaskInTenant(taskId)`.
- `HrmRecruitAgentTaskServiceImpl.java:137-149` checks only `TenantContextHolder.getTenantId()` vs `task.tenantId`.
- **Impact:** any user with `hrm:recruit:agent:audit-query` can read **any** agent task/result in the tenant (including tasks submitted by other recruiters with masked resume content). Portal path is safer: `getTask(userId, taskId)` → `selectByIdAndUserId` (`HrmRecruitAgentTaskServiceImpl.java:124-129`).

### R4 — Coarse portal permission `hrm:portal:query` over-privileges
- One permission gates salary slips, insurance, attendance export, performance pages, employee archive reads (see §3.1 and menu SQL `hrm_menu.sql:150-160`).
- **Impact:** cannot grant “read my attendance” without also granting salary-slip read. Not an IDOR, but weak least-privilege for multi-role employees.

### R5 — Cross-tenant public job board is intentional but widens data exposure
- `HrmPortalRecruitController.java:88-210` `@TenantIgnore` + `TenantUtils.executeIgnore` enumerates **all portal tenants** and posts.
- Company profiles and hiring tenant IDs are aggregated across tenants (`:157-176`).
- **Impact:** by design (marketplace). Residual risk: overly rich post/company fields, and `buildApplication` loads candidate via `TenantUtils.executeIgnore` (`:278`) then masks only mobile (`:283`). Ensure other PII on application VO is never populated.

### R6 — Offer accept ownership is solid; confirm cascade must stay in tenant
- Ownership: `HrmRecruitOfferServiceImpl.java:627-636` + re-check `:433-438`.
- **Residual:** `getCandidateOfferApplication` uses `TenantUtils.executeIgnore` to find offers by id. Sequential offer IDs could be probed; response is `RECRUIT_OFFER_STATE_INVALID` when not owner (good), but the probe confirms existence/timing. Prefer `selectByIdAndApplicantUserId` style queries to avoid oracle.

### R7 — Data-permission deliberately excludes salary / insurance / recruit
- `HrmDataPermissionConfiguration.java:48-50`.
- **Impact:** admin salary/insurance/recruit lists rely on **permission strings + tenant filter only**, not dept/self data-range. A dept manager with `hrm:salary:*` sees all salaries in tenant unless app-layer filters exist. Portal insurance/salary are self-scoped in controller, so employee-side is OK.

### R8 — Anonymous RPC + ops endpoints permitted in module security
- `SecurityConfiguration.java:28-33` — `/actuator/**`, `/druid/**`, `ApiConstants.PREFIX/**` permitAll.
- **Impact:** if the HRM service port is reachable beyond the gateway/service mesh, actuator/druid/RPC are unauthenticated at the Spring Security layer. Depends on network policy.

### R9 — Weak/uniform PreAuthorize on certificate & training-experience
- `HrmPortalEmployeeCertificateController.java:38`, `HrmPortalEmployeeTrainingExperienceController.java:38` use `isAuthenticated()` only.
- Service still scopes by `validateEmployeeBySelf` (`:40`), so no IDOR today; inconsistent with sibling controllers and menu permissions.

### R10 — Performance portal mutations mix owner checks and role-based handlers
- Owner-checked: `fill-quota` (`HrmPortalPerformanceAssessmentController.java:206`), `confirm-result` (`:260`), `submit-appeal` (`:271`) via `validateOwnPerformanceAssessment` (`:284-286`).
- Handler-role methods (`score`, `reject-review-stage`, `handle-result-audit`, `handle-appeal`) pass `getLoginUserId()` into services that must validate stage assignment (`validateTaskStage` referenced at `:294`).
- **Impact:** if a service path skips `validateTaskStage`, any user with `hrm:portal:performance:action` could score/handle arbitrary assessments. QA should assert reviewer/handler identity on each mutation.

### R11 — Job-side data permission bypass (no login user)
- Documented `HrmDataPermissionConfiguration.java:42`: no login → no dept/self filter.
- **Impact:** a buggy job query that forgets explicit scoping can read/write entire tenant tables. `@TenantJob` limits blast radius to one tenant per execution.

### R12 — Resume masking is LLM-egress only
- `HrmSensitiveTextMasker` used in `HrmResumeAgentPayloadUtils` and scene runner; portal application response masks mobile only (`HrmPortalRecruitController.java:283`).
- **Impact:** admin resume download/preview (`HrmRecruitResumeController`) returns full files to `hrm:recruit:candidate:query` holders — expected, but combined with R3’s task result sharing this can leak richer resume-derived AI output across recruiters.

---

## Appendix A — Quick endpoint ↔ permission matrix (portal)

| Method | Path | Annotation |
|---|---|---|
| GET | `/hrm/portal/home/calendar` | `hrm:portal:query` |
| GET | `/hrm/portal/employee/get-bind-status` | `hrm:portal:query` |
| GET | `/hrm/portal/employee/get` | `hrm:portal:query` |
| PUT | `/hrm/portal/employee/update` | `hrm:portal:employee:update` |
| GET | `/hrm/portal/employee/*/list` (contact/education/work) | `hrm:portal:query` |
| GET | `/hrm/portal/employee/certificate/list` | `isAuthenticated()` |
| GET | `/hrm/portal/employee/training-experience/list` | `isAuthenticated()` |
| GET | `/hrm/portal/attendance/*` | `hrm:portal:query` |
| POST/PUT | `/hrm/portal/attendance/leave/create|cancel` | `hrm:portal:attendance:leave` |
| GET | `/hrm/portal/insurance/record/*` | `hrm:portal:query` |
| GET/PUT | `/hrm/portal/salary/slip/*` | `hrm:portal:query` |
| GET | `/hrm/portal/performance/assessment/*` (reads) | `hrm:portal:query` |
| PUT/POST | `/hrm/portal/performance/assessment/*` (mutations) | `hrm:portal:performance:action` |
| GET | `/hrm/portal/recruit/post|company/*` | `@PermitAll` |
| POST/GET/PUT | `/hrm/portal/recruit/application/*` | **none** |
| * | `/hrm/portal/recruit/chat/*` | `isAuthenticated()` |
| PUT | `/hrm/portal/recruit/offer/accept|reject` | `isAuthenticated()` |
| * | `/hrm/portal/resume*`, `/hrm/portal/resume-version*`, `/hrm/portal/recruit-agent/*` | **none** |

## Appendix B — Key source roots

```
txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/
  controller/admin/portal/**          # employee + public recruit portal
  controller/admin/recruit/**         # HR admin recruit + agent
  framework/security/config/          # SecurityConfiguration
  framework/datapermission/config/    # HrmDataPermissionConfiguration
  job/{certificate,contract,employee,performance,recruit,salary}/
  service/{agent,personal/resume,recruit,employee,performance,salary,insurance}/
  util/HrmSensitiveTextMasker.java
  util/HrmResumeAgentPayloadUtils.java

txgy-ui-admin-vben/apps/web-antd/src/views/hrm/portal/**     # employee portal UI
txgy-ui-admin-vben/apps/web-antd/src/views/portal/recruit/** # public recruit UI
txgy-ui-admin-uniapp-master/src/pages-hrm/portal/**          # mobile employee portal
sql/hrm_menu.sql
sql/2026-09-17-hrm-recruit-agent-permissions.sql
sql/2026-09-03-hrm-recruit-groups-integration.sql
```
