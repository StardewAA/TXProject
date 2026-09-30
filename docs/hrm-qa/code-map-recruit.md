# HRM Recruit Domain — Deep Code Map

> Scope: `txgy-module-hrm/txgy-module-hrm-server` recruit packages + admin UI `txgy-ui-admin-vben/apps/web-antd/src/views/hrm/recruit/**`.
> Paths below are relative to `D:\桌面\codex\txgycloud\` unless absolute.

---

## 1. Package / Service / Controller Inventory

Base Java package: `cn.iocoder.txgy.module.hrm`

### 1.1 Controllers (admin)

| Domain | Controller | Base path | Notes |
|---|---|---|---|
| demand | `controller/admin/recruit/HrmRecruitDemandController.java` | `/hrm/recruit/demand` | CRUD + `update-status` |
| post | `controller/admin/recruit/HrmRecruitPostController.java` | `/hrm/recruit/post` | CRUD + `update-status`, `status-count`, `validate-status` |
| post-type | `controller/admin/recruit/HrmRecruitPostTypeController.java` | `/hrm/recruit/post-type` | leaf type tree |
| candidate | `controller/admin/recruit/HrmRecruitCandidateController.java` | `/hrm/recruit/candidate` | create/update/status/eliminate/convert-employee |
| application | `controller/admin/recruit/HrmRecruitApplicationController.java` | `/hrm/recruit/application` | pipeline transitions (screen/interview/eliminate/withdraw) |
| offer | `controller/admin/recruit/HrmRecruitOfferController.java` | `/hrm/recruit/offer` | create/submit/approve/send/confirm/onboard/withdraw/bpm-callback |
| interview | `controller/admin/recruit/HrmRecruitInterviewController.java` | `/hrm/recruit/interview` | create/update/result/start/evaluate/delete |
| resume | `controller/admin/recruit/HrmRecruitResumeController.java` | `/hrm/recruit/resume` | snapshot versions + preview/download |
| chat | `controller/admin/recruit/chat/HrmAdminRecruitChatController.java` | `/hrm/recruit/chat` | conversation list/send/convert-candidate/end/delete |
| company | `controller/admin/recruit/company/HrmRecruitCompanyProfileController.java` | `/hrm/recruit/company` | single profile get/save |
| channel | `controller/admin/recruit/HrmRecruitChannelController.java` | `/hrm/recruit/channel` | CRUD + status + soft delete remap |
| config | `controller/admin/recruit/HrmRecruitConfigController.java` | `/hrm/recruit/config` | eliminate-reason list/save |
| agent | `controller/admin/recruit/agent/HrmRecruitAgentController.java` | `/hrm/recruit/agent` | AI summary/match/interview-question + task get |

Portal (candidate-facing) companions under `controller/admin/portal/recruit/` and `controller/admin/portal/agent/`:
- `HrmPortalRecruitController` (public posts, apply, withdraw)
- `HrmPortalRecruitChatController`
- `HrmPortalRecruitOfferController` (accept/reject)
- `HrmPortalRecruitAgentController`

RPC callback: `service/recruit/application/listener/HrmRecruitOfferApprovalListener.java` exposes `/rpc-api/hrm/recruit/offer/update-approval-status`.

### 1.2 Services

| Domain | Interface / Impl |
|---|---|
| demand | `service/recruit/demand/HrmRecruitDemandService(Impl)` |
| post | `service/recruit/post/HrmRecruitPostService(Impl)`, `HrmRecruitPostTypeService(Impl)` |
| candidate | `service/recruit/candidate/HrmRecruitCandidateService(Impl)` |
| interview | `service/recruit/candidate/HrmRecruitInterviewService(Impl)` |
| application | `service/recruit/application/HrmRecruitApplicationService(Impl)` |
| offer | `service/recruit/application/HrmRecruitOfferService(Impl)` + `service/recruit/offer/HrmOfferPdfService(Impl)` |
| resume | `service/recruit/resume/HrmRecruitResumeService(Impl)` |
| chat | `service/recruit/chat/HrmRecruitChatService(Impl)` |
| company | `service/recruit/company/HrmRecruitCompanyProfileService(Impl)` |
| channel | `service/recruit/config/HrmRecruitChannelService(Impl)` |
| config | `service/recruit/config/HrmRecruitConfigService(Impl)` |
| agent | `service/agent/HrmRecruitAgentAdminService(Impl)`, `HrmRecruitAgentAnalyzeService(Impl)`, `HrmRecruitAgentTaskService(Impl)`, `HrmRecruitAgentSceneRunner` |

Jobs (`job/recruit/`):
- `HrmRecruitOfferExpireJob` (`hrmRecruitOfferExpireJob`) — batch expire overdue offers
- `HrmRecruitInterviewReminderJob`
- `HrmRecruitChatTimeoutJob`

### 1.3 DAL / Enums / Framework

- Mappers: `dal/mysql/recruit/**` and `dal/mysql/agent/**`
- Enums: `enums/recruit/**` (application/candidate/chat/company/demand/post) and `enums/agent/**`
- Framework: `framework/agent/**` (LLM client + prompts), `framework/operatelog/core/HrmRecruit*ParseFunction`

---

## 2. Key DO / Tables (`dal/dataobject`)

| DO | Table | Role |
|---|---|---|
| `recruit/demand/HrmRecruitDemandDO` | `hrm_recruit_demand` | headcount request; `headcount` / `onboardCount` / `status` |
| `recruit/post/HrmRecruitPostDO` | `hrm_recruit_post` | job post; links `demandId`, `postTypeId`, `channelId`, `deptId`, `ownerEmployeeId`, `status`, `stopReason` |
| `recruit/post/HrmRecruitPostTypeDO` | `hrm_recruit_post_type` | leaf post type (`CommonStatusEnum`) |
| `recruit/candidate/HrmRecruitCandidateDO` | `hrm_recruit_candidate` | person projection; `status`, `stageNumber`, `postId`, `channelId`, `entryTime`, `resumeUrls` |
| `recruit/candidate/HrmRecruitInterviewDO` | `hrm_recruit_interview` | interview round; `applicationId` (nullable for legacy), `stageNumber`/`roundNo`, `result` |
| `recruit/application/HrmRecruitApplicationDO` | `hrm_recruit_application` | **source of truth pipeline row**; `status` (string enum), `version` optimistic lock, `employeeId`, `onboardTime`, reasons |
| `recruit/application/HrmRecruitApplicationLogDO` | `hrm_recruit_application_log` | transition audit (`fromStatus`/`toStatus`/`action`/`reason`) |
| `recruit/application/HrmRecruitOfferDO` | `hrm_recruit_offer` | offer; `approvalStatus`, `approvalVersion`, `processInstanceId`, salary, `validUntil`, `employeeId` |
| `recruit/resume/HrmRecruitResumeDO` | `hrm_recruit_resume` | resume snapshot pinned to application; `contentHash`, `postFingerprint`, `sourcePersonalVersionId` |
| `recruit/chat/HrmRecruitChatConversationDO` | `hrm_recruit_chat_conversation` | pre-apply chat; `status`, `applicationStatus`, `applicationId` |
| `recruit/chat/HrmRecruitChatMessageDO` | `hrm_recruit_chat_message` | chat messages |
| `recruit/company/HrmRecruitCompanyProfileDO` | `hrm_recruit_company_profile` | employer branding / portal company page |
| `recruit/config/HrmRecruitChannelDO` | `hrm_recruit_channel` | source channel (`systemFlag`, `CommonStatusEnum`) |
| `agent/HrmRecruitAgentTaskDO` | `hrm_recruit_agent_task` | async AI task; `idempotencyKey` |
| `agent/HrmRecruitAgentAnalysisDO` | `hrm_recruit_agent_analysis` | AI analysis result |
| `agent/HrmRecruitAgentOptimizeItemDO` | `hrm_recruit_agent_optimize_item` | resume optimize items |

Related employee-side tables written during onboard: `hrm_employee`, `hrm_employee_contract`, contract sign records (via `HrmEmployeeContractService`, `HrmContractSignRecordService`).

---

## 3. State Machines (with transition citations)

### 3.1 Job post status — `HrmRecruitPostStatusEnum`

| Code | Name |
|---|---|
| 0 | PAUSED 已暂停 |
| 1 | PUBLISHED 已发布 |
| 2 | CLOSED 已关闭 |

Transitions enforced in `service/recruit/post/HrmRecruitPostServiceImpl.java`:

| From → To | Allowed | Cite |
|---|---|---|
| create → PUBLISHED | always (create hard-sets published) | `HrmRecruitPostServiceImpl.java:71` |
| PUBLISHED → PAUSED / CLOSED | yes | `HrmRecruitPostServiceImpl.java:205-207` |
| PAUSED → PUBLISHED / CLOSED | yes | `HrmRecruitPostServiceImpl.java:209-211` |
| CLOSED → * | **no** (terminal) | `HrmRecruitPostServiceImpl.java:213` (`return false`) |

Status write + stopReason: `HrmRecruitPostServiceImpl.java:172-197`.

### 3.2 Candidate stage — `HrmRecruitCandidateStatusEnum`

| Code | Name |
|---|---|
| 1 NEW | 新候选人 |
| 2 PRIMARY_PASS | 初选通过 |
| 3 INTERVIEW | 安排面试 |
| 4 INTERVIEW_PASS | 面试通过 |
| 5 OFFER_SENT | 已发 offer |
| 6 PENDING_ENTRY | 待入职 |
| 7 ELIMINATED | 已淘汰 |
| 8 JOINED | 工入职 |

**Projection mapping** from application status: `HrmRecruitCandidateStatusEnum.fromApplicationStatus` at `enums/recruit/candidate/HrmRecruitCandidateStatusEnum.java:91-109`. Applied by `syncRecruitCandidateProjection` at `HrmRecruitCandidateServiceImpl.java:537-561`.

**Manual transitions** (legacy path without application) `isStatusTransitionAllowed` at `HrmRecruitCandidateServiceImpl.java:272-292`:

| From → To | Cite |
|---|---|
| NEW → PRIMARY_PASS / INTERVIEW_PASS | `:274-276` |
| PRIMARY_PASS → NEW / INTERVIEW_PASS | `:279-281` |
| INTERVIEW_PASS → OFFER_SENT / NEW / PRIMARY_PASS | `:284-287` |
| ELIMINATED → NEW only | `:290-291` |
| PENDING_ENTRY → JOINED (entry confirm) | `HrmRecruitCandidateServiceImpl.java:296-315` |

Allowed action sets: `INTERVIEW_ARRANGE_STATUSES` `:57-58`, `ELIMINATE_STATUSES` `:63-65`, `CONVERT_EMPLOYEE_STATUSES` `:70-71`.

### 3.3 Application status (pipeline source of truth) — `HrmRecruitApplicationStatusEnum`

String enum: `SUBMITTED, SCREENING, RESUME_REJECTED, SCREENING_PASSED, PENDING_INTERVIEW, INTERVIEW_SCHEDULED, INTERVIEWING, INTERVIEW_EVALUATING, NEXT_INTERVIEW, INTERVIEW_CANCELED, INTERVIEW_ABSENT, INTERVIEW_REJECTED, FINAL_INTERVIEW_PASSED, PENDING_OFFER, PASSED, OFFER_APPROVING, OFFER_SENT, OFFER_REJECTED, OFFER_WITHDRAWN, OFFER_EXPIRED, OFFER_CANDIDATE_REJECTED, PENDING_ONBOARD, ONBOARDED, ELIMINATED, WITHDRAWN`.

Full transition matrix `isAllowed` at `service/recruit/application/HrmRecruitApplicationServiceImpl.java:586-673`. Terminal set `isTerminal()` at `enums/recruit/application/HrmRecruitApplicationStatusEnum.java:48-53`.

Representative transitions (all go through `transition()` `:532-566` with `selectByIdForUpdate` + `updateStatusWithVersion`):

| Transition | Method / action | Cite |
|---|---|---|
| → SUBMITTED | create / portal create | `:86`, `:490` |
| SUBMITTED → SCREENING | `startScreening` | `:157-158` |
| SCREENING → SCREENING_PASSED | `passScreening` | `:163-164` |
| * → RESUME_REJECTED | `rejectResume` | `:169-170` |
| → PENDING_INTERVIEW | `enterInterview` | `:175-185` |
| → INTERVIEW_SCHEDULED | `markInterviewScheduled` | `:197-204` |
| → INTERVIEWING | `markInterviewStarted` | `:209-211` |
| → INTERVIEW_EVALUATING | `markInterviewEvaluating` | `:216-218` |
| INTERVIEWING → PASSED / FINAL_INTERVIEW_PASSED | evaluate decision | `:636-639`, `:669-672` |
| → PENDING_OFFER (offer create) | `markPendingOffer` | `:258-260` |
| → OFFER_APPROVING | `markOfferApproving` | `:286-288` |
| → OFFER_SENT | `markOfferSent` | `:279-281` |
| OFFER_SENT → PENDING_ONBOARD | `markPendingOnboard` | `:331-333` |
| PENDING_ONBOARD → ONBOARDED | `markOnboarded` | `:362-379` |
| OFFER_* rejections | `markOfferRejected/Withdrawn/Expired/CandidateRejected` | `:299-326` |

### 3.4 Offer approval status — `HrmRecruitOfferApprovalStatusEnum`

`DRAFT → APPROVING → APPROVED|REJECTED → SENT → CONFIRMED`; also `WITHDRAWN`, `EXPIRED`, `CANDIDATE_REJECTED`.

| Transition | Cite |
|---|---|
| create → DRAFT | `HrmRecruitOfferServiceImpl.java:136` |
| DRAFT → APPROVING (submit) | `:142-147` |
| APPROVING → APPROVED/REJECTED (callback, versioned) | `:270-290` |
| DRAFT/APPROVING/APPROVED/SENT → WITHDRAWN | `:296-308` |
| SENT → EXPIRED | `:324-333` |
| APPROVED **or DRAFT** → SENT (send allows draft bypass) | `:335-350` |
| SENT → CONFIRMED (accept) | `:418-425` |
| SENT → CANDIDATE_REJECTED | `:444-460` |
| CONFIRMED → onboard creates employee | `:487-571` |

### 3.5 Demand status — `HrmRecruitDemandStatusEnum`

`1 PENDING 待招聘 → 2 RECRUITING 招聘中 → 3 FINISHED 已完成`; `4 CANCELED 已取消`.

| Transition | Cite |
|---|---|
| create → PENDING (default) | `HrmRecruitDemandServiceImpl.java:48-49` |
| any ↔ any via `update-status` (**no matrix**) | `:69-77` |
| onboard +1 → RECRUITING if still PENDING | `:145-146` |
| onboardCount ≥ headcount → FINISHED | `:143-144` |
| FINISHED/CANCELED block further onboard | `:159-163` |

---

## 4. Concurrency / Idempotency Risks

### 4.1 Offer accept / confirm

| Risk | Evidence | Severity |
|---|---|---|
| Accept path locks offer via `selectByIdForUpdate` then `confirm` | `HrmRecruitOfferServiceImpl.java:418-440`, lock helper `:638-641` | OK (row lock) |
| Two concurrent accepts: second fails state check on `SENT` | `:420`, `:436` | OK |
| `expireOverdueOffers` runs inside accept/reject **and** list/read APIs | `:430`, `:448`, `HrmRecruitApplicationServiceImpl.java:122`, `:503`, `:513` | Medium — side effects on GET; concurrent expire vs accept can race on same offer (expire has no row lock before update `:478-483`) |
| Confirm requires `validUntil` non-null and not past; send treats null as unlimited | `:421-422` vs `:344-345` | Medium — inconsistent validity semantics |

### 4.2 Onboard

| Risk | Evidence | Severity |
|---|---|---|
| Idempotent early return if `offer.employeeId` already set | `HrmRecruitOfferServiceImpl.java:488-489` | OK |
| Also early return if `application.employeeId` set | `:493` | OK |
| `markOnboarded` / `linkEmployeeForOnboard` use version CAS | `HrmRecruitApplicationServiceImpl.java:349-355`, `:370-376` | OK |
| Demand headcount check then later increment is **not one atomic unit** | check `:542` + `HrmRecruitDemandServiceImpl.java:154-169`; increment later `:566` + `:123-150`（中间 `createEmployee :561`） | **High** — two concurrent onboards can both pass `onboardCount >= headcount` check then both increment past headcount |
| Demand increment itself is atomic SQL | `HrmRecruitDemandMapper.increaseOnboardCount` `:47` | OK for counter, not for quota |
| Dual onboard paths (`offer.onboard` vs `candidate.convert-employee`) | `HrmRecruitOfferServiceImpl.java:487` vs `HrmRecruitCandidateServiceImpl.java:425` | Medium — convert path does not always mirror offer validation (blacklist/mobile/employmentType/demand) |
| Side-effect failures swallowed (demand rewrite, onboard docs, contract, mail) | `:545-559`, `:575-609`, `:375-416` | Medium — partial success by design; no compensating retry ledger |

### 4.3 Approval callbacks

| Risk | Evidence | Severity |
|---|---|---|
| Optimistic lock on approve | `updateApprovalWithVersion` `HrmRecruitOfferMapper.java:49`; fail if 0 rows `HrmRecruitOfferServiceImpl.java:289` | OK |
| Replay idempotent if already terminal with same processInstanceId+version | `:276-279` | OK |
| Manual approve synthesizes `MANUAL-<userId>` processInstanceId | `:201-204` | Medium — concurrent manual approve + BPM callback can interleave; version guards most cases |
| BPM listener is HTTP `/rpc-api/...` with no obvious auth/idempotency key | `HrmRecruitOfferApprovalListener.java:39-42` | Medium — relies solely on version CAS |
| Process cancel best-effort | `:310-318` | Low — withdrawn offer may leave open BPM task |
| Self-approve default **allowed** | `isSelfApprovalAllowed` `:77-89` | Medium (control risk) |

### 4.4 Application / candidate concurrency

| Risk | Evidence | Severity |
|---|---|---|
| Application transitions: `selectByIdForUpdate` + version CAS | `HrmRecruitApplicationServiceImpl.java:534`, `:559` | OK |
| Portal create locks existing candidate by mobile | `:431` | OK |
| Duplicate processing application check then insert (TOCTOU without unique DB constraint visible in code) | `:80-83`, `:100-103`, `:416-419`, `:436-439` | Medium |
| Candidate projection updates after application commit path without version | `HrmRecruitCandidateServiceImpl.java:559-560` | Low — last-write-wins projection |
| Demand number generation: count+retry 5, fallback timestamp | `HrmRecruitDemandServiceImpl.java:175-188` | Medium — concurrent create can collide |

### 4.5 Agent idempotency

- Client-supplied `idempotencyKey` looked up before create: `HrmRecruitAgentTaskServiceImpl.java:38`, `:62`.
- Frontend builder `buildAgentIdempotencyKey` in `txgy-ui-admin-vben/apps/web-antd/src/api/hrm/recruit/agent/index.ts:132+`.

---

## 5. Permission Strings (`hrm:recruit:*`)

Collected from `@PreAuthorize` on controllers + `sql/hrm_menu.sql` / `sql/2026-*-hrm-recruit-*.sql`.

| Area | Permissions |
|---|---|
| demand | `hrm:recruit:demand:create\|update\|delete\|query` |
| post | `hrm:recruit:post:create\|update\|query` |
| post-type | `hrm:recruit:post-type:create\|update\|delete\|query` (query OR post:query) |
| candidate | `hrm:recruit:candidate:create\|update\|delete\|query` (create menu disabled in `sql/2026-09-20-hrm-candidate-portal-only.sql`) |
| application | `hrm:recruit:application:create\|query\|screen\|eliminate\|interview\|withdraw` |
| offer | `hrm:recruit:offer:create\|query\|submit\|approve\|withdraw\|send\|confirm\|onboard` |
| interview | `hrm:recruit:interview:create\|update\|delete\|query` |
| resume | reuses `hrm:recruit:candidate:update` / `query` (`HrmRecruitResumeController.java:38-80`) |
| chat | `hrm:recruit:chat:query\|reply\|convert-candidate\|end\|delete` |
| company | `hrm:recruit:company:query\|update` |
| channel | `hrm:recruit:channel:create\|update\|delete\|query` |
| config | `hrm:recruit:config:update\|query` (query OR candidate:update) |
| agent | `hrm:recruit:agent:candidate-summary\|application-match\|interview-question\|audit-query` |

Menu components map 1:1 to views (`sql/hrm_menu.sql:22+`): `hrm/recruit/post/index`, `hrm/recruit/candidate/index`, `hrm/recruit/channel/index`, `hrm/recruit/chat/index`, `hrm/recruit/demand`, `hrm/recruit/company`, `hrm/recruit/setting/eliminate/index`, `hrm/recruit/post-type/index`.

---

## 6. Data Flow: demand → post → application/resume → candidate → interview → offer → employee onboard

```
hrm_recruit_demand (REQ-xxx)
        │ demandId
        ▼
hrm_recruit_post (status PUBLISHED/PAUSED/CLOSED) ── postTypeId / channelId
        │ postId
        │
        │  (portal apply / admin create / chat convert)
        ▼
hrm_recruit_resume  ← snapshot of personal resume (contentHash, postFingerprint)
        │ resumeId
        ▼
hrm_recruit_application (status string, version)  ── applicantUserId, candidateId
        │ writes hrm_recruit_application_log on every transition
        │
        │ syncRecruitCandidateProjection
        ▼
hrm_recruit_candidate (status 1..8, stageNumber)   ← projection / legacy dual-write
        │
        ▼
hrm_recruit_interview (result 1..5, stageNumber/roundNo)
        │ markInterview* on application
        ▼
hrm_recruit_offer (approvalStatus, approvalVersion)
        │ create → application PENDING_OFFER
        │ submit → OFFER_APPROVING (+ optional BPM hrm_offer_approval)
        │ send   → OFFER_SENT + contract draft + mail
        │ confirm→ PENDING_ONBOARD
        ▼
onboard / convert-employee
        │ writes hrm_employee (+ contract bind/template/render)
        │ markOnboarded → application ONBOARDED + employeeId
        │ increaseOnboardCount(demandId) → demand RECRUITING/FINISHED
        ▼
hrm_employee (entryStatus ACTIVE → candidate JOINED)
```

What is written where (summary):

| Step | Primary write | Side writes |
|---|---|---|
| Create demand | `hrm_recruit_demand` | demandNo `REQ-yyyyMMdd-###` |
| Create/update post | `hrm_recruit_post` | optional demand link `demandId` |
| Portal apply | `hrm_recruit_application` + `hrm_recruit_resume` snapshot | upsert `hrm_recruit_candidate`; chat `applicationStatus=SUBMITTED` |
| Screening / interview | `hrm_recruit_application.status` | `hrm_recruit_application_log`; `hrm_recruit_interview` |
| Interview result | `hrm_recruit_interview.result` | application `markInterview*`; candidate projection |
| Offer create | `hrm_recruit_offer` DRAFT | application `PENDING_OFFER` |
| Offer send | offer SENT | `hrm_employee_contract` draft; mail with PDF |
| Offer confirm | offer CONFIRMED | application `PENDING_ONBOARD` |
| Onboard | `hrm_employee` | offer.employeeId; demand onboardCount; onboard docs; contract file + sign record; application `ONBOARDED` |

---

## 7. Frontend Routes / Pages ↔ APIs

### 7.1 Explicit routes — `txgy-ui-admin-vben/apps/web-antd/src/router/routes/modules/hrm.ts`

| Route | Component | Purpose |
|---|---|---|
| `/hrm/recruit/chat` | `views/hrm/recruit/chat/index.vue` | admin chat inbox |
| `/hrm/recruit/post-type` | `views/hrm/recruit/post-type/index.vue` | post types |
| `/hrm/recruit/candidate/detail/:id` | `views/hrm/recruit/candidate/detail/index.vue` | candidate 360 |
| `/hrm/recruit/post/detail/:id` | `views/hrm/recruit/post/detail/index.vue` | post detail |

Menu-driven pages (component paths from `sql/hrm_menu.sql`):
- `hrm/recruit/post/index` → `views/hrm/recruit/post/index.vue`
- `hrm/recruit/candidate/index` → `views/hrm/recruit/candidate/index.vue`
- `hrm/recruit/channel/index` → `views/hrm/recruit/channel/index.vue`
- `hrm/recruit/demand` → `views/hrm/recruit/demand/index.vue`
- `hrm/recruit/company` → `views/hrm/recruit/company/index.vue`
- `hrm/recruit/setting/eliminate/index` → `views/hrm/recruit/setting/eliminate/index.vue`

### 7.2 View → API mapping (`apps/web-antd/src/api/hrm/recruit/**`)

| View / module | APIs used |
|---|---|
| `demand/index.vue`, `demand/modules/form.vue` | `/hrm/recruit/demand/*` |
| `post/index.vue`, `post/modules/form.vue`, `stop-form.vue` | `/hrm/recruit/post/*` |
| `post/detail/**` | `getRecruitPost` |
| `post-type/index.vue` | `/hrm/recruit/post-type/*` |
| `candidate/index.vue` | candidate CRUD/status + application transitions + interview start |
| `candidate/modules/form.vue` | candidate create/update |
| `candidate/modules/eliminate-form.vue` | `eliminateRecruitApplication` **or** `eliminateRecruitCandidate` |
| `candidate/modules/interview-form.vue` | `/hrm/recruit/interview/create|update` |
| `candidate/modules/interview-result-form.vue` | `updateRecruitInterviewResult` |
| `candidate/modules/interview-evaluation-form.vue` | `evaluateRecruitInterview` |
| `candidate/modules/offer-form.vue` | `createRecruitOffer`, `submitRecruitOffer` |
| `candidate/modules/offer-status-form.vue` | offer send/withdraw/approve/confirm/resend |
| `candidate/modules/onboard-form.vue` | `onboardRecruitOffer` |
| `candidate/modules/*-batch-form.vue` | status/post/channel batch updates |
| `candidate/detail/modules/application-list.vue` | `getRecruitApplicationPage` |
| `candidate/detail/modules/offer-list.vue` | `getRecruitOfferListByCandidate` |
| `candidate/detail/modules/resume-versions.vue` | `/hrm/recruit/resume/*` |
| `candidate/detail/modules/interview-list.vue` | `getRecruitInterviewListByCandidate` |
| `candidate/detail/modules/ai-assistant.vue` | `/hrm/recruit/agent/*` + application APIs |
| `candidate/detail/modules/recruit-chat.vue` | chat APIs |
| `channel/**` | `/hrm/recruit/channel/*` |
| `company/index.vue` | `/hrm/recruit/company/*` |
| `setting/eliminate/**` | `/hrm/recruit/config/*` |
| `chat/index.vue` | admin chat controller APIs |

Portal routes (out of admin menu but same domain) in `router/routes/core.ts`: `/portal/recruit`, `/recruit/detail`, `/recruit/company`, `/recruit/messages`, `/recruit/profile-setup`.

---

## 8. Notable Defects / Risks

### 8.1 Validation gaps

1. **Demand status has no transition matrix** — `updateRecruitDemandStatus` accepts any valid enum value from any state (`HrmRecruitDemandServiceImpl.java:69-77`). FINISHED can be flipped back to PENDING; CANCELED can go to RECRUITING.
2. **Offer `send` allows DRAFT bypass** — comment admits “免审批直接发放” (`HrmRecruitOfferServiceImpl.java:337-346`). Combined with `hrm:recruit:offer:send`, approval can be skipped entirely.
3. **Self-approve default true** — `hrm.offer.approval.allow-self-approve` defaults allow (`:77-89`, `:194-199`).
4. **`validUntil` semantics diverge** — send treats null as unlimited (`:344-345`); confirm **requires** non-null future date (`:421-422`). Offer sent without validity cannot be confirmed.
5. **Create offer requires `PASSED` or `FINAL_INTERVIEW_PASSED`** (`:116-118`) but `skip-interview` jumps there from screening (`HrmRecruitApplicationServiceImpl.java:190-192`); interview evidence optional.
6. **Post create always PUBLISHED** (`HrmRecruitPostServiceImpl.java:71`) — no draft state; PAUSED is only via explicit status update.
7. **Mobile uniqueness is service-level only** (`HrmRecruitCandidateServiceImpl.java:137-145`) — no cited DB unique index in code; race can create duplicate candidates.

### 8.2 Race conditions

1. **Demand headcount quota race (High)** — `validateRecruitDemandCanRecruit` (read, `:542`) then later `increaseOnboardCount` (write, `:566`) are separate (`HrmRecruitOfferServiceImpl.java`). Concurrent onboards can exceed `headcount`. Note: DRAFT send skip-approval is intentional (业务确认 2026-09-23, `:337-345`).
2. **Expire-overdue side effects on read paths** — `getApplicationPage` / portal getters call `expireOverdueOffers` (`HrmRecruitApplicationServiceImpl.java:122`, `:503`, `:513`). Expire updates offer without `selectByIdForUpdate` (`HrmRecruitOfferServiceImpl.java:478-483`) vs accept’s row lock.
3. **Application duplicate check TOCTOU** — `selectProcessingByCandidateAndPost` then insert (`HrmRecruitApplicationServiceImpl.java:80-89`) without unique constraint evidence.
4. **Demand number generator** — count + 5 retries (`HrmRecruitDemandServiceImpl.java:175-188`) under concurrency.

### 8.3 Inconsistent status enums / dual models

1. **Three status languages** — candidate uses Integer 1–8; application uses long string codes; offer uses separate approval string enum. Mapping is lossy (`WITHDRAWN → null` in `fromApplicationStatus:108`).
2. **Legacy vs application dual path** — interviews with `applicationId == null` update candidate status directly (`HrmRecruitInterviewServiceImpl.java:366-387`); application-linked path uses fine-grained states. Two code paths for same business event.
3. **Candidate `isStatusTransitionAllowed` incomplete vs projection** — manual path forbids INTERVIEW→INTERVIEW_PASS etc., while projection can set arbitrary mapped status (`HrmRecruitCandidateServiceImpl.java:272-292` vs `:559-560`).
4. **`INTERVIEW_CANCELED` / `INTERVIEW_ABSENT` are non-terminal** but mapped to candidate INTERVIEW; `INTERVIEW_REJECTED` is terminal application-wise (`HrmRecruitApplicationStatusEnum.java:48-52`) while cancel/absent can resume (`HrmRecruitApplicationServiceImpl.java:618-621`).
5. **Post status PAUSED=0** while many other tables use `CommonStatusEnum` 0=disable / 1=enable — easy to confuse in UI filters.
6. **Company profile `status` default 0** (`HrmRecruitCompanyProfileServiceImpl.java:39`) with `HrmRecruitCompanyProfileStatusEnum` — unclear published/unpublished contract vs portal.

### 8.4 Other

- BPM process key `hrm_offer_approval` optional; silent fallback to manual (`HrmRecruitOfferServiceImpl.java:149-162`).
- Mail/contract/onboard-doc failures deliberately non-blocking — no outbox/retry table for recruit side effects.
- `eliminateRecruitCandidate` **deletes** pre-created employee (`HrmRecruitCandidateServiceImpl.java:405-410`) — destructive side effect on eliminate.
- Candidate create permission intentionally disabled in menu SQL (`sql/2026-09-20-hrm-candidate-portal-only.sql`) while controller still exposes `hrm:recruit:candidate:create`.

---

## Appendix: Quick file index

| Concern | File |
|---|---|
| Application SM | `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationServiceImpl.java` |
| Offer SM + onboard | `.../service/recruit/application/HrmRecruitOfferServiceImpl.java` |
| Candidate SM + projection | `.../service/recruit/candidate/HrmRecruitCandidateServiceImpl.java` |
| Interview SM | `.../service/recruit/candidate/HrmRecruitInterviewServiceImpl.java` |
| Post SM | `.../service/recruit/post/HrmRecruitPostServiceImpl.java` |
| Demand SM | `.../service/recruit/demand/HrmRecruitDemandServiceImpl.java` |
| Status enums | `.../enums/recruit/**` |
| DOs | `.../dal/dataobject/recruit/**`, `.../dal/dataobject/agent/**` |
| Admin UI | `txgy-ui-admin-vben/apps/web-antd/src/views/hrm/recruit/**` |
| Admin APIs | `txgy-ui-admin-vben/apps/web-antd/src/api/hrm/recruit/**` |
| Routes | `txgy-ui-admin-vben/apps/web-antd/src/router/routes/modules/hrm.ts` |
| Permission seed | `sql/hrm_menu.sql`, `sql/2026-*-hrm-recruit-*.sql` |
