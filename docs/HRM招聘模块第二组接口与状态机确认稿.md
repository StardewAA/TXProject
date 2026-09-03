# HRM 招聘模块第二组接口与状态机确认稿

**版本**：V20260903.001  
**范围**：应聘、筛选、面试、Offer、录用转员工  
**状态**：待产品、第一组、BPM/员工模块共同确认

## 1. 文档定位

本稿将《招聘模块-分组.md》中的第二组边界转换为可开发契约。附件中的分组、规则和命名均作为参考约束；以下标注“待确认”的内容在确认前不得写死在代码中。

第二组不维护职位基础字段、候选人人才池基础资料、简历文件底层能力；通过第一组提供的查询和文件授权接口使用这些数据。

## 2. 领域关系

```text
候选人(candidate) 1 ── N 应聘(application)
职位(post)       1 ── N 应聘(application)
应聘(application) 1 ── N 面试(interview)
应聘(application) 1 ── 0..1 Offer(offer)
Offer            1 ── 0..1 员工(employee)
```

`application` 是第二组的聚合根。面试、Offer 必须关联 `application_id`，不再仅以 `candidate_id` 判断当前流程，以支持一个候选人多次应聘。

核心字段：

| 对象 | 必填关联/字段 | 说明 |
| --- | --- | --- |
| application | `candidate_id`、`post_id`、`resume_id`、`channel_id`、`status` | 一次投递一条记录，保存投递时的简历版本 |
| interview | `application_id`、`round_no`、`interview_time`、面试官 | 同一应聘可多轮，`round_no` 唯一 |
| offer | `application_id`、薪资建议、入职日期、岗位、`approval_status` | 同一应聘最多一份有效 Offer |
| employee 回写 | `employee_id`、`onboard_time` | 以 `application_id` 作为转员工幂等键 |

## 3. 应聘状态机

### 3.1 状态字典

| 编码 | 名称 | 终态 | 说明 |
| --- | --- | --- | --- |
| `SUBMITTED` | 已投递 | 否 | 应聘创建成功 |
| `SCREENING` | 筛选中 | 否 | HR 已开始处理 |
| `INTERVIEWING` | 面试中 | 否 | 已安排至少一轮未结束面试 |
| `PASSED` | 面试通过 | 否 | 达到录用前置条件 |
| `OFFER_APPROVING` | Offer 审批中 | 否 | Offer 已提交 BPM |
| `OFFER_SENT` | 已发 Offer | 否 | BPM 审批完成且已发送/确认 |
| `PENDING_ONBOARD` | 待入职 | 否 | 等待实际入职确认 |
| `ONBOARDED` | 已入职 | 是 | 员工创建成功并已回写 |
| `ELIMINATED` | 已淘汰 | 是 | 必须有淘汰原因 |
| `WITHDRAWN` | 已撤回 | 是 | 候选人或 HR 撤回 |

### 3.2 允许的流转

| 当前状态 | 允许动作 | 目标状态 | 操作角色 |
| --- | --- | --- | --- |
| `SUBMITTED` | 开始筛选 | `SCREENING` | HR |
| `SUBMITTED`/`SCREENING` | 淘汰 | `ELIMINATED` | HR/负责人 |
| `SUBMITTED`/`SCREENING` | 撤回 | `WITHDRAWN` | 候选人/HR |
| `SCREENING` | 安排面试 | `INTERVIEWING` | HR |
| `INTERVIEWING` | 面试通过 | `PASSED` | 授权 HR/负责人 |
| `INTERVIEWING` | 面试不通过 | `ELIMINATED` | 授权面试官/HR |
| `PASSED` | 提交 Offer | `OFFER_APPROVING` | HR |
| `OFFER_APPROVING` | 审批驳回 | `PASSED` | BPM 回调 |
| `OFFER_APPROVING` | 审批通过并发出 | `OFFER_SENT` | BPM 回调/HR |
| `OFFER_SENT` | 候选人确认 | `PENDING_ONBOARD` | HR |
| `PENDING_ONBOARD` | 确认入职 | `ONBOARDED` | HR/员工模块 |
| `OFFER_SENT`/`PENDING_ONBOARD` | 放弃入职 | `WITHDRAWN` | HR |

终态不可直接修改；重新投递应新建 `application`。每次流转写入状态历史和操作日志。

## 4. API 契约（管理端）

接口沿用现有 HRM Controller 风格（`/hrm/recruit/...`、`CommonResult`、`@PreAuthorize`）。所有写接口支持统一幂等请求头 `request-id`，自动注入租户上下文。

### 4.1 应聘

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/hrm/recruit/application/create` | 创建应聘 | `hrm:recruit:application:create` |
| `GET` | `/hrm/recruit/application/page` | 分页查询 | `...:query` |
| `GET` | `/hrm/recruit/application/get?id=` | 详情及时间线 | `...:query` |
| `PUT` | `/hrm/recruit/application/start-screening` | 开始筛选 | `...:screen` |
| `PUT` | `/hrm/recruit/application/eliminate` | 淘汰并记录原因 | `...:eliminate` |
| `PUT` | `/hrm/recruit/application/withdraw` | 撤回 | `...:withdraw` |
| `GET` | `/hrm/recruit/application/status-count` | 状态统计 | `...:query` |

创建请求：`candidateId`、`postId`、`resumeId`、`channelId`、`sourceRemark`。后端校验职位状态、候选人与简历归属、重复进行中应聘和租户一致性。

### 4.2 面试

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/hrm/recruit/interview/create` | 创建面试轮次 |
| `PUT` | `/hrm/recruit/interview/update` | 修改未开始面试 |
| `PUT` | `/hrm/recruit/interview/update-result` | 录入通过/不通过/取消及评价 |
| `GET` | `/hrm/recruit/interview/page` | 按应聘、面试官、日期查询 |
| `GET` | `/hrm/recruit/interview/list-by-application` | 查询应聘全部轮次 |
| `PUT` | `/hrm/recruit/interview/cancel` | 取消面试 |

现有 `/hrm/recruit/interview` 能力可复用，但需增加 `applicationId`、`roundNo`，并将候选人状态回写改为应聘状态回写。

### 4.3 Offer 与转员工

| 方法 | 路径 | 用途 | 前置条件 |
| --- | --- | --- | --- |
| `POST` | `/hrm/recruit/offer/create` | 创建 Offer 草稿 | 应聘为 `PASSED` |
| `PUT` | `/hrm/recruit/offer/update` | 修改草稿 | 未提交审批 |
| `POST` | `/hrm/recruit/offer/{id}/submit` | 提交 BPM 审批 | 字段完整 |
| `PUT` | `/hrm/recruit/offer/{id}/withdraw` | 撤回审批 | BPM 允许撤回 |
| `GET` | `/hrm/recruit/offer/page` | Offer 分页 | 数据范围 |
| `GET` | `/hrm/recruit/offer/get?id=` | Offer 详情 | 数据范围 |
| `POST` | `/hrm/recruit/offer/{id}/onboard` | 进入待入职 | 审批完成 |
| `POST` | `/hrm/recruit/offer/{id}/confirm-onboard` | 转员工并回写 | `PENDING_ONBOARD` |

审批回调建议：`POST /hrm/recruit/offer/bpm-callback`，使用 `processInstanceId + approvalVersion` 幂等；回调仅更新审批和业务状态，不接受前端伪造的审批结果。

## 5. 统一响应与错误码

列表返回基座标准分页结构；详情返回对象、状态、状态历史和操作权限。建议错误码：

| 错误码 | 含义 |
| --- | --- |
| `RECRUIT_APPLICATION_DUPLICATE` | 存在同职位进行中应聘 |
| `RECRUIT_POST_NOT_OPEN` | 职位不可投递 |
| `RECRUIT_STATUS_TRANSITION_INVALID` | 当前状态不允许该操作 |
| `RECRUIT_INTERVIEW_ROUND_DUPLICATE` | 面试轮次重复 |
| `RECRUIT_OFFER_PRECONDITION_INVALID` | 未达到 Offer 前置条件 |
| `RECRUIT_OFFER_APPROVAL_REQUIRED` | Offer 尚未审批完成 |
| `RECRUIT_ONBOARD_IDEMPOTENT_EXISTS` | 已生成员工，返回既有员工编号 |
| `RECRUIT_SENSITIVE_FIELD_DENIED` | 无权查看敏感字段 |

## 6. 权限与数据安全

- HR：本授权组织范围内全流程操作；负责人：本部门应聘及审批范围；面试官：仅被分派应聘和必要简历字段。
- 面试评价默认仅评价人、HR 和指定复核人可见；候选人不可见内部评价。
- 手机号、证件号默认脱敏；导出单独授权并记录条件、字段和文件编号。
- 每次状态、审批、淘汰、撤回、转员工均写入操作日志；跨租户和越权请求统一拒绝。

## 7. 联调与验收用例

1. 同候选人投递两个职位成功；同职位存在进行中应聘时重复投递被阻断。
2. 同一应聘创建三轮面试，分别录入结果，状态只由最新有效轮次驱动。
3. 面试不通过必须填写淘汰原因；取消面试不应直接结束应聘。
4. 未通过 BPM 审批的 Offer 不能进入待入职。
5. 重复调用转员工接口只生成一个员工，返回同一 `employee_id`。
6. 员工创建失败可重试，重试不重复创建 Offer 或应聘回写。
7. 职位关闭后不能新增应聘，但历史应聘仍可面试、查询和完成入职。
8. 面试官、部门负责人、HR、候选人分别验证字段和数据范围。

## 8. 待确认事项

请产品/企业在开发前确认：

1. 同职位重复投递的“进行中”状态集合及再次投递冷却期。
2. 候选人撤回权限、撤回后是否允许再次投递。
3. 面试通过的判定方式：单轮通过、末轮通过，还是全部轮次通过。
4. Offer 审批节点、审批人来源、候选人确认方式和 Offer 有效期。
5. `OFFER_SENT` 到 `PENDING_ONBOARD` 是否需要候选人签署或 HR 手工确认。
6. 转员工是否自动创建平台用户、员工编号规则及失败补偿责任方。
7. 现有 `hrm_recruit_interview` 数据迁移到 `application_id` 的映射策略。

确认后即可冻结 DDL、OpenAPI、权限菜单和状态枚举，进入应用模块的后端与前端并行开发。
