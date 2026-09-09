# HRM 招应聘模块详细流程图

**依据**：当前 HRM 招聘模块后端实现及其第二组设计稿。  
**范围**：职位发布、候选人/简历、投递、筛选、多轮面试、Offer、录用转员工。  
**核心聚合根**：`application`（应聘记录）；同一候选人可应聘多个职位，同一应聘可包含多轮面试，但最多关联一份有效 Offer。

> 图中“门户”指外部候选人使用的公开招聘入口；“后台”指 HR、部门负责人和面试官使用的管理端。应聘状态变更由服务层统一校验并记录状态日志，前端不能直接改写状态。

## 1. 业务对象与关联关系

```mermaid
classDiagram
direction LR

class 招聘职位 {
  +post_id
  +状态：已发布/已暂停/已关闭
  +招聘负责人
  +职位要求与有效期
}
class 候选人 {
  +candidate_id
  +姓名、手机号、邮箱
  +人才池基础资料
}
class 简历版本 {
  +resume_id
  +候选人编号
  +文件、哈希、附件关系
}
class 应聘记录 {
  +application_id
  +candidate_id
  +post_id
  +resume_id
  +状态与版本号
  +淘汰/撤回原因
  +employee_id
}
class 状态历史 {
  +from_status
  +to_status
  +action、reason
  +操作人、时间
}
class 面试轮次 {
  +interview_id
  +application_id
  +round_no
  +面试时间、面试官、评价
  +结果：未完成/通过/未通过/取消
}
class Offer {
  +offer_id
  +application_id
  +薪资建议、入职日期、岗位
  +审批状态与审批版本
  +employee_id
}
class 员工档案 {
  +employee_id
  +入职信息
  +候选人来源标识
}

招聘职位 "1" --> "0..*" 应聘记录 : 接收投递
候选人 "1" --> "0..*" 简历版本 : 维护
候选人 "1" --> "0..*" 应聘记录 : 多次应聘
简历版本 "1" --> "0..*" 应聘记录 : 投递快照
应聘记录 "1" --> "0..*" 状态历史 : 留痕
应聘记录 "1" --> "0..*" 面试轮次 : 多轮面试
应聘记录 "1" --> "0..1" Offer : 录用流程
Offer "1" --> "0..1" 员工档案 : 转员工
应聘记录 "1" --> "0..1" 员工档案 : 回写 employee_id
```

## 2. 招应聘端到端主流程

```mermaid
flowchart TB
  classDef startEnd fill:#1D4ED8,stroke:#1E3A8A,color:#fff
  classDef candidate fill:#DBEAFE,stroke:#2563EB,color:#172554
  classDef hr fill:#DCFCE7,stroke:#16A34A,color:#14532D
  classDef system fill:#FEF3C7,stroke:#D97706,color:#78350F
  classDef decision fill:#FCE7F3,stroke:#DB2777,color:#831843
  classDef terminal fill:#FEE2E2,stroke:#DC2626,color:#7F1D1D

  Start([开始]):::startEnd

  subgraph P[职位与投递]
    direction TB
    P1[HR 创建/维护职位]:::hr
  P2{职位状态为<br/>已发布？}:::decision
    P3[门户展示公开职位]:::candidate
    P4[候选人填写资料并选择/上传简历]:::candidate
    P5[后台 HR 创建应聘<br/>或门户在线投递]:::candidate
    P6[创建应聘记录：SUBMITTED<br/>保存职位、候选人、简历版本快照]:::system
    P7[写入 application 状态历史]:::system
    P8[拒绝投递并返回原因]:::terminal
  end

  subgraph V[投递校验]
    direction TB
    V1{职位仍为已发布<br/>且租户一致？}:::decision
    V2{候选人、简历存在且<br/>简历归属正确？}:::decision
    V3{同候选人、同职位<br/>是否已有进行中应聘？}:::decision
  end

  subgraph S[筛选与面试]
    direction TB
    S1[HR 开始筛选<br/>SUBMITTED → SCREENING]:::hr
    S2{筛选通过？}:::decision
    S3[淘汰：记录原因<br/>→ ELIMINATED]:::terminal
    S4[HR 进入面试<br/>SCREENING → INTERVIEWING]:::hr
    S5[创建/安排第 N 轮面试<br/>指定面试官并发送通知]:::hr
    S6[面试官录入结果与评价]:::hr
    S7{本轮结果}:::decision
    S8[面试取消：保留记录<br/>可重新安排，不结束应聘]:::system
    S10[面试通过<br/>INTERVIEWING → PASSED]:::system
    S11[面试未通过：评价作为原因<br/>INTERVIEWING → ELIMINATED]:::terminal
  end

  subgraph O[Offer 与入职]
    direction TB
    O1[HR 创建 Offer 草稿<br/>仅 PASSED 可创建]:::hr
    O2[填写薪资建议、入职日期、岗位等]:::hr
    O3[提交 BPM 审批<br/>PASSED → OFFER_APPROVING]:::system
    O4{BPM 审批通过？}:::decision
    O5[审批驳回<br/>Offer=REJECTED<br/>应聘退回 PASSED]:::terminal
    O6[HR 发出 Offer<br/>Offer=SENT<br/>应聘 → OFFER_SENT]:::hr
    O7{Offer 是否确认？<br/>当前由管理端 /confirm 承接}:::decision
    O8[保持 OFFER_SENT，等待确认<br/>当前代码无此状态直接撤回]:::system
    O9[确认 Offer<br/>Offer=CONFIRMED<br/>应聘 → PENDING_ONBOARD]:::system
    O10[HR 确认实际入职并创建员工档案]:::hr
    O11{已存在 employee_id？}:::decision
    O12[返回已建员工编号<br/>不重复建档]:::system
    O13[创建员工档案，回写 Offer 与应聘 employee_id<br/>应聘 → ONBOARDED]:::system
  end

  Start --> P1 --> P2
  P2 -- 否：已暂停/已关闭 --> P8
  P2 -- 是 --> P3 --> P4 --> P5 --> V1
  V1 -- 否 --> P8
  V1 -- 是 --> V2
  V2 -- 否 --> P8
  V2 -- 是 --> V3
  V3 -- 是：阻断重复投递 --> P8
  V3 -- 否 --> P6 --> P7 --> S1 --> S2
  S2 -- 否 --> S3
  S2 -- 是 --> S4 --> S5 --> S6 --> S7
  S7 -- 取消 --> S8 --> S5
  S7 -- 通过 --> S10 --> O1 --> O2 --> O3 --> O4
  S7 -- 未通过 --> S11
  O4 -- 否 --> O5 --> O1
  O4 -- 是 --> O6 --> O7
  O7 -- 否 --> O8
  O7 -- 是 --> O9 --> O10 --> O11
  O11 -- 是 --> O12
  O11 -- 否 --> O13
```

## 3. 应聘状态机

```mermaid
stateDiagram-v2
  direction LR
  [*] --> SUBMITTED : 创建投递
  SUBMITTED : 已投递
  SCREENING : 筛选中
  INTERVIEWING : 面试中
  PASSED : 面试通过
  OFFER_APPROVING : Offer 审批中
  OFFER_SENT : 已发 Offer
  PENDING_ONBOARD : 待入职
  ONBOARDED : 已入职（终态）
  ELIMINATED : 已淘汰（终态）
  WITHDRAWN : 已撤回（终态）

  SUBMITTED --> SCREENING : HR 开始筛选
  SUBMITTED --> ELIMINATED : HR/负责人淘汰（必填原因）
  SUBMITTED --> WITHDRAWN : 候选人或 HR 撤回（必填原因）

  SCREENING --> INTERVIEWING : HR 进入面试
  SCREENING --> ELIMINATED : HR/负责人淘汰（必填原因）
  SCREENING --> WITHDRAWN : 候选人或 HR 撤回（必填原因）

  INTERVIEWING --> PASSED : 当前有效面试轮通过
  INTERVIEWING --> ELIMINATED : 当前有效面试轮未通过（评价为原因）
  INTERVIEWING --> WITHDRAWN : 候选人或 HR 撤回（必填原因）

  PASSED --> OFFER_APPROVING : Offer 草稿提交 BPM
  OFFER_APPROVING --> PASSED : BPM 审批驳回
  OFFER_APPROVING --> OFFER_SENT : 审批通过后 HR 发出 Offer
  OFFER_SENT --> PENDING_ONBOARD : /confirm（当前由管理端承接）
  OFFER_SENT --> WITHDRAWN : 设计稿约定（当前代码尚未放开）
  PENDING_ONBOARD --> ONBOARDED : /onboard 创建员工并回写 employee_id
  PENDING_ONBOARD --> WITHDRAWN : 设计稿约定（当前代码尚未放开）

  ONBOARDED --> [*]
  ELIMINATED --> [*]
  WITHDRAWN --> [*]
```

**进行中状态**：`SUBMITTED`、`SCREENING`、`INTERVIEWING`、`PASSED`、`OFFER_APPROVING`、`OFFER_SENT`、`PENDING_ONBOARD`。同一租户、同一候选人、同一职位存在进行中记录时，系统阻断再次投递；进入 `ONBOARDED`、`ELIMINATED` 或 `WITHDRAWN` 后，如需再次应聘必须新建记录。

## 4. 多轮面试与状态回写流程

```mermaid
flowchart LR
  classDef action fill:#DBEAFE,stroke:#2563EB,color:#172554
  classDef system fill:#FEF3C7,stroke:#D97706,color:#78350F
  classDef check fill:#FCE7F3,stroke:#DB2777,color:#831843
  classDef stop fill:#FEE2E2,stroke:#DC2626,color:#7F1D1D

  A[应聘状态 INTERVIEWING]:::action --> B[HR 安排/创建面试轮次]:::action
  B --> C[校验 application_id、面试官在职状态<br/>生成或递增 round_no]:::system
  C --> D[发送站内信给主面试官及其他面试官]:::system
  D --> E[面试官录入当前轮结果]:::action
  E --> F{当前轮是否为<br/>最新有效轮？}:::check
  F -- 否 --> G[拒绝更新：不是当前面试]:::stop
  F -- 是 --> H{面试结果}:::check
  H -- 未完成 --> I[拒绝结束：结果必须是通过、未通过或取消]:::stop
  H -- 取消 --> J[保留取消原因<br/>不改变应聘状态]:::system
  J --> B
  H -- 通过 --> L[回写候选人状态<br/>并将应聘置为 PASSED]:::system
  H -- 未通过 --> M[评价作为淘汰原因<br/>应聘置为 ELIMINATED]:::stop
```

> 当前实现会根据当前有效面试轮的通过/未通过结果立即回写应聘状态；面试记录支持按 `application_id` 递增轮次，但“通过后是否仍允许追加面试、何时达到录用条件”（单轮、末轮或全部轮次）仍需产品冻结并补齐一致性规则。

## 5. Offer 审批与转员工时序

```mermaid
sequenceDiagram
  autonumber
  actor HR as HR/招聘负责人
  participant App as 应聘服务
  participant Offer as Offer 服务
  participant BPM as BPM 审批引擎
  actor Candidate as 候选人
  participant Employee as 员工档案服务
  participant Audit as 状态历史/操作日志

  HR->>Offer: 创建 Offer（application_id、薪资、入职日期、岗位）
  Offer->>App: 校验应聘状态为 PASSED
  App-->>Offer: 校验通过
  Offer->>Offer: 保存 Offer = DRAFT

  HR->>Offer: 提交审批
  Offer->>Offer: DRAFT → APPROVING；递增 approval_version
  Offer->>App: PASSED → OFFER_APPROVING
  App->>Audit: 写入 OFFER_SUBMIT 状态历史
  Offer->>BPM: 创建/关联审批流程

  BPM-->>Offer: 审批回调（processInstanceId、approvalVersion、结果）
  Offer->>Offer: 校验审批状态、流程实例和版本
  alt 审批驳回
    Offer->>Offer: APPROVING → REJECTED
    Offer->>App: OFFER_APPROVING → PASSED
    App->>Audit: 写入 OFFER_REJECTED 状态历史
  else 审批通过
    Offer->>Offer: APPROVING → APPROVED
    HR->>Offer: 发出 Offer
    Offer->>Offer: APPROVED → SENT，记录 sent_time
    Offer->>App: OFFER_APPROVING → OFFER_SENT
    App->>Audit: 写入 OFFER_SENT 状态历史
    Candidate-->>HR: 线下/业务入口确认 Offer
    HR->>Offer: /confirm（当前管理端接口）
    Offer->>Offer: SENT → CONFIRMED，记录 confirm_time
    Offer->>App: OFFER_SENT → PENDING_ONBOARD
    App->>Audit: 写入 OFFER_CONFIRMED 状态历史
  end

  HR->>Offer: 确认入职
  Offer->>Offer: 检查 Offer / 应聘是否已有 employee_id
  alt 已经建档
    Offer-->>HR: 返回既有 employee_id（幂等）
  else 首次建档
    Offer->>Employee: 创建员工（从候选人带入基础资料）
    Employee-->>Offer: employee_id
    Offer->>Offer: 回写 Offer.employee_id
    Offer->>App: 回写 application.employee_id；PENDING_ONBOARD → ONBOARDED
    App->>Audit: 写入 ONBOARDED 状态历史
    Offer-->>HR: 返回 employee_id
  end
```

## 6. 关键控制点与异常分支

```mermaid
flowchart TB
  classDef control fill:#DCFCE7,stroke:#16A34A,color:#14532D
  classDef reject fill:#FEE2E2,stroke:#DC2626,color:#7F1D1D
  classDef audit fill:#EDE9FE,stroke:#7C3AED,color:#3B0764
  classDef integration fill:#FEF3C7,stroke:#D97706,color:#78350F

  A[任一写操作] --> B{权限、组织数据范围、租户一致性<br/>均校验通过？}
  B -- 否 --> R1[拒绝越权/跨租户请求]:::reject
  B -- 是 --> C{当前状态允许目标动作？}
  C -- 否 --> R2[返回非法状态流转错误]:::reject
  C -- 是 --> D{是否需要原因<br/>且原因已填写？}
  D -- 否 --> R3[拒绝：淘汰/撤回原因必填]:::reject
  D -- 是 --> E{并发版本更新成功？}
  E -- 否 --> R4[拒绝：数据已被其他请求推进<br/>需刷新后重试]:::reject
  E -- 是 --> F[更新业务记录]:::control
  F --> G[写 application 状态历史与操作日志]:::audit
  G --> H{是否依赖外部服务？}
  H -- 面试通知 --> I[调用消息服务发送面试安排]:::integration
  H -- Offer 审批 --> J[仅接受 BPM 回调的审批结果]:::integration
  H -- 转员工 --> K[员工创建与应聘回写在同一事务<br/>失败可安全重试]:::integration
  H -- 否 --> L[返回处理成功]:::control
  I --> L
  J --> L
  K --> L
```

| 控制项 | 当前规则 |
| --- | --- |
| 职位可投递性 | 仅“已发布”职位可新增投递；暂停或关闭均拒绝新增投递，历史流程可继续查询和完成。 |
| 重复投递 | 事务内检查同租户、同候选人、同职位的进行中应聘，不用永久唯一约束阻断历史再次投递。 |
| 状态一致性 | 应聘状态仅由服务层流转；每次创建、筛选、淘汰、撤回、面试结论、Offer、入职均写状态历史。 |
| 并发保护 | 应聘状态流转携带 `version` 乐观锁；Offer 审批回调使用流程实例和审批版本实现幂等。 |
| 转员工幂等 | Offer 或应聘已回写 `employee_id` 时直接返回既有编号，避免重复创建员工。 |
| 数据与隐私 | 以租户和授权组织范围隔离数据；手机号、证件号默认脱敏；面试评价只向授权人员展示；导出单独授权并留痕。 |
| 当前实现差异 | `OFFER_SENT/PENDING_ONBOARD → WITHDRAWN` 已在设计稿中提出，但当前 `HrmRecruitApplicationServiceImpl.isAllowed()` 未开放；Offer `/confirm` 及门户撤回均由后端权限接口承接，候选人独立确认入口仍待补齐。 |

## 7. 角色泳道

```mermaid
flowchart LR
  classDef candidate fill:#DBEAFE,stroke:#2563EB,color:#172554
  classDef hr fill:#DCFCE7,stroke:#16A34A,color:#14532D
  classDef interviewer fill:#F3E8FF,stroke:#9333EA,color:#581C87
  classDef bpm fill:#FEF3C7,stroke:#D97706,color:#78350F
  classDef system fill:#E5E7EB,stroke:#4B5563,color:#111827

  subgraph C[候选人]
    C1[浏览公开职位]:::candidate
    C2[在线投递/上传简历]:::candidate
    C3[查看本人应聘与面试安排]:::candidate
    C4[撤回应聘；Offer 确认由候选人确认或 HR 代确认]:::candidate
  end
  subgraph H[HR / 招聘负责人]
    H1[发布、暂停或关闭职位]:::hr
    H2[创建应聘、筛选、淘汰]:::hr
    H3[安排多轮面试]:::hr
    H4[创建/提交/发出 Offer]:::hr
    H5[确认入职]:::hr
  end
  subgraph I[面试官]
    I1[接收面试安排通知]:::interviewer
    I2[查看被分派应聘的必要信息]:::interviewer
    I3[录入面试评价和结论]:::interviewer
  end
  subgraph B[BPM 审批引擎]
    B1[审批 Offer]:::bpm
    B2[回调审批通过/驳回结果]:::bpm
  end
  subgraph Y[HRM 系统]
    Y1[校验权限、数据范围、租户、状态和版本]:::system
    Y2[维护应聘状态历史、Offer 与面试记录]:::system
    Y3[发送消息、创建员工、回写 employee_id]:::system
  end

  C1 --> C2 --> Y1
  H1 --> Y1
  H2 --> Y1
  H3 --> Y1 --> Y2 --> I1 --> I2 --> I3 --> Y1
  H4 --> Y1 --> B1 --> B2 --> Y1
  C3 --> Y2
  C4 --> Y1
  H5 --> Y3
```

## 8. 当前接口映射

| 流程环节 | 当前接口 | 说明 |
| --- | --- | --- |
| 公开职位浏览 | `GET /hrm/portal/recruit/post/page`、`GET /hrm/portal/recruit/post/get` | 仅查询已发布职位；公开接口跨租户读取。 |
| 门户在线投递 | `POST /hrm/portal/recruit/application/create` | 服务端按登录用户/手机号查找或创建候选人，复制简历到招聘简历并生成 `SUBMITTED`。 |
| 我的应聘 | `GET /hrm/portal/recruit/application/page`、`GET /hrm/portal/recruit/application/get` | 仅返回当前登录候选人的应聘、面试和 Offer 信息。 |
| 门户撤回 | `PUT /hrm/portal/recruit/application/withdraw` | 由后端再次执行状态流转和原因必填校验。 |
| 后台创建/查询应聘 | `POST /hrm/recruit/application/create`、`GET /hrm/recruit/application/page`、`GET /hrm/recruit/application/get` | 创建前校验候选人、职位、简历归属和进行中重复投递。 |
| 筛选与应聘状态 | `PUT /hrm/recruit/application/start-screening`、`/enter-interview`、`/eliminate`、`/withdraw` | 服务层按当前状态和 `version` 乐观锁更新，并写状态日志。 |
| 面试安排与结果 | `POST /hrm/recruit/interview/create`、`PUT /update`、`PUT /update-result`、`GET /get`、`GET /list-by-candidate`、`DELETE /delete` | 面试优先关联 `application_id`，按 `round_no` 支持多轮；当前有效轮结果回写应聘状态。 |
| Offer 草稿与审批 | `POST /hrm/recruit/offer/create`、`PUT /submit`、`PUT /approval-callback`、`PUT /withdraw` | `PASSED` 才能创建；BPM 回调校验 `processInstanceId + approvalVersion`。 |
| Offer 发出/确认 | `PUT /hrm/recruit/offer/send`、`PUT /hrm/recruit/offer/confirm` | `APPROVED → SENT → CONFIRMED`，分别回写 `OFFER_SENT`、`PENDING_ONBOARD`。 |
| 转员工 | `PUT /hrm/recruit/offer/onboard` | 复用员工建档服务；按 Offer 和 `application_id` 的 `employee_id` 幂等。 |

## 9. 待业务确认的流程节点

下列节点在实现中已预留，但尚需企业确认后作为最终规则冻结：

1. 同职位再次投递的冷却期，以及候选人撤回后的再次投递限制；
2. 面试通过的判定口径：单轮、末轮或全部轮次通过；
3. Offer BPM 审批节点、审批人来源、Offer 有效期及候选人确认方式；
4. `OFFER_SENT` 到 `PENDING_ONBOARD` 是否必须完成电子签署；
5. 转员工时是否自动创建平台账号、员工编号规则及失败补偿责任方；
6. 历史面试记录补齐 `application_id` 与 `round_no` 的迁移策略。
