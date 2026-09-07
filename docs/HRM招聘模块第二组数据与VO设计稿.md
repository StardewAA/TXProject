# HRM 招聘模块第二组：数据表、状态枚举与 VO 设计稿

**版本**：V20260903.002  
**状态**：待产品、第一组、BPM、员工模块评审  
**适用范围**：应聘、筛选、面试、Offer、录用转员工

> 本稿是开发设计稿，不是直接执行的生产 DDL。`candidate_id`、`post_id`、`resume_id` 等基础对象由第一组提供查询和租户校验能力。

## 1. 设计原则

1. `hrm_recruit_application` 是应聘流程聚合根；面试和 Offer 必须以 `application_id` 关联。
2. 所有业务表继承基座审计字段：`creator`、`create_time`、`updater`、`update_time`、`deleted`、`tenant_id`。
3. 不设置物理外键，使用服务层校验跨模块引用；所有查询必须带租户条件。
4. 历史状态不可覆盖，状态变化写入 `hrm_recruit_application_log`。
5. 重复投递按“同租户、同职位、同候选人、进行中状态”在事务内校验，不能用包含历史记录的永久唯一约束。

## 2. 表结构草案

### 2.1 应聘主表：`hrm_recruit_application`

```sql
CREATE TABLE `hrm_recruit_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应聘编号',
  `candidate_id` bigint NOT NULL COMMENT '候选人编号，引用 hrm_recruit_candidate',
  `post_id` bigint NOT NULL COMMENT '职位编号，引用 hrm_recruit_post',
  `resume_id` bigint DEFAULT NULL COMMENT '投递时使用的简历版本编号',
  `channel_id` bigint DEFAULT NULL COMMENT '招聘渠道编号',
  `status` varchar(32) NOT NULL COMMENT '应聘状态，见 HrmRecruitApplicationStatusEnum',
  `status_update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '状态更新时间',
  `eliminate_reason` varchar(500) DEFAULT NULL COMMENT '淘汰原因',
  `withdraw_reason` varchar(500) DEFAULT NULL COMMENT '撤回原因',
  `source_remark` varchar(500) DEFAULT NULL COMMENT '投递备注/来源说明',
  `employee_id` bigint DEFAULT NULL COMMENT '转员工后的员工编号',
  `onboard_time` datetime DEFAULT NULL COMMENT '实际入职时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_application_candidate` (`tenant_id`,`candidate_id`),
  KEY `idx_application_post` (`tenant_id`,`post_id`),
  KEY `idx_application_status` (`tenant_id`,`status`,`status_update_time`),
  KEY `idx_application_employee` (`tenant_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘应聘记录';
```

进行中状态建议为：`SUBMITTED`、`SCREENING`、`INTERVIEWING`、`PASSED`、`OFFER_APPROVING`、`OFFER_SENT`、`PENDING_ONBOARD`。重复投递校验使用该集合，淘汰和撤回历史记录允许再次投递。

### 2.2 状态日志表：`hrm_recruit_application_log`

```sql
CREATE TABLE `hrm_recruit_application_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '状态日志编号',
  `application_id` bigint NOT NULL COMMENT '应聘编号',
  `from_status` varchar(32) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(32) NOT NULL COMMENT '目标状态',
  `action` varchar(32) NOT NULL COMMENT '动作编码',
  `reason` varchar(500) DEFAULT NULL COMMENT '操作原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作用户编号',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号',
  `request_id` varchar(64) DEFAULT NULL COMMENT '幂等请求号',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_application_log_app` (`tenant_id`,`application_id`,`create_time`),
  KEY `idx_application_log_request` (`tenant_id`,`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 应聘状态与操作日志';
```

### 2.3 面试表调整：`hrm_recruit_interview`

在现有表基础上新增并调整：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `application_id` | `bigint NOT NULL` | 新主关联；历史数据迁移后必填 |
| `round_no` | `int NOT NULL` | 同一 `application_id` 下唯一且从 1 开始 |
| `result` | `varchar(32)` | `UNFINISHED`、`PASS`、`NOT_PASS`、`CANCEL` |
| `evaluate_visibility` | `varchar(32)` | `ASSIGNEE_ONLY`、`HR_ONLY`、`ALL_AUTHORIZED` |
| `evaluate` | `text` | 面试评价，按字段权限返回 |
| `cancel_reason` | `varchar(500)` | 取消时必填 |

建议索引：`(tenant_id, application_id, round_no)`、`(tenant_id, interview_time)`、`(tenant_id, interview_employee_id)`。迁移期保留 `candidate_id`，新代码以 `application_id` 为准。

### 2.4 Offer 表：`hrm_recruit_offer`

```sql
CREATE TABLE `hrm_recruit_offer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Offer 编号',
  `application_id` bigint NOT NULL COMMENT '应聘编号',
  `offer_no` varchar(64) NOT NULL COMMENT 'Offer 业务编号',
  `approval_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `salary_amount` decimal(12,2) DEFAULT NULL COMMENT '薪资建议',
  `salary_unit` tinyint DEFAULT NULL COMMENT '薪资单位，引用字典',
  `post_id` bigint NOT NULL COMMENT '录用职位编号',
  `entry_post_id` bigint DEFAULT NULL COMMENT '入职岗位编号，引用基座岗位',
  `planned_onboard_date` date DEFAULT NULL COMMENT '计划入职日期',
  `valid_until` date DEFAULT NULL COMMENT 'Offer 有效期',
  `attachment_ids` varchar(2000) DEFAULT NULL COMMENT '附件编号列表，统一文件服务鉴权',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号',
  `approval_version` int NOT NULL DEFAULT 0 COMMENT '审批版本',
  `employee_id` bigint DEFAULT NULL COMMENT '转员工后的员工编号',
  `sent_time` datetime DEFAULT NULL COMMENT '发出时间', `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_offer_no` (`tenant_id`,`offer_no`),
  KEY `idx_offer_application` (`tenant_id`,`application_id`), KEY `idx_offer_status` (`tenant_id`,`approval_status`),
  KEY `idx_offer_process` (`tenant_id`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 招聘 Offer 录用记录';
```

同一应聘最多一份有效 Offer 由服务层校验；审批驳回后可在原 Offer 上修订版本，已发出 Offer 不允许覆盖关键薪资和入职字段。

## 3. Java 状态枚举

包路径建议：`cn.iocoder.txgy.module.hrm.enums.recruit.application`。

```java
public enum HrmRecruitApplicationStatusEnum {
    SUBMITTED("已投递"), SCREENING("筛选中"), INTERVIEWING("面试中"),
    PASSED("面试通过"), OFFER_APPROVING("Offer审批中"), OFFER_SENT("已发Offer"),
    PENDING_ONBOARD("待入职"), ONBOARDED("已入职"),
    ELIMINATED("已淘汰"), WITHDRAWN("已撤回");
}
```

```java
public enum HrmRecruitOfferApprovalStatusEnum {
    DRAFT("草稿"), APPROVING("审批中"), APPROVED("审批通过"),
    REJECTED("已驳回"), WITHDRAWN("已撤回"), SENT("已发出"), CONFIRMED("已确认");
}
```

```java
public enum HrmRecruitInterviewResultEnum {
    UNFINISHED("未完成"), PASS("通过"), NOT_PASS("不通过"), CANCEL("已取消");
}
```

枚举应提供 `getStatus()`/`getName()` 或项目统一的 `getValue()`/`getLabel()` 方法，并在转换服务中集中维护允许流转矩阵。

## 4. 请求 VO 设计

包路径建议：`controller.admin.recruit.vo.application`、`...vo.interview`、`...vo.offer`。字段使用 `Long`、`LocalDate`、`LocalDateTime`，沿用 `@Schema`、`@NotNull`、`@Size`、`@DateTimeFormat`。

### 4.1 应聘 VO

**`HrmRecruitApplicationCreateReqVO`**

| 字段 | 必填 | 校验/说明 |
| --- | --- | --- |
| `candidateId` | 是 | 第一组候选人详情必须存在 |
| `postId` | 是 | 职位状态必须允许投递 |
| `resumeId` | 是 | 简历版本必须属于候选人 |
| `channelId` | 否 | 渠道必须属于当前租户 |
| `sourceRemark` | 否 | ≤500 字符 |
| `requestId` | 是 | 幂等请求号，建议从 Header 读取 |

**`HrmRecruitApplicationPageReqVO`**：`candidateId`、`postId`、`status`、`channelId`、`keyword`、`createTime[]`、分页字段。

**`HrmRecruitApplicationStatusReqVO`**：`id`、`reason`；淘汰时 `reason` 必填。

**`HrmRecruitApplicationRespVO`**：`id`、候选人摘要（脱敏）、职位摘要、`resumeId`、渠道、`status`/`statusName`、`statusUpdateTime`、最新面试摘要、Offer 摘要、`employeeId`、时间线、当前用户可用操作集合。

### 4.2 面试 VO

**`HrmRecruitInterviewSaveReqVO`**：`id`、`applicationId`、`roundNo`、`type`、`interviewEmployeeId`、`otherInterviewEmployeeIds`、`interviewTime`、`address`、`remark`。

**`HrmRecruitInterviewResultReqVO`**：`id`、`result`、`evaluate`、`cancelReason`、`evaluateVisibility`。`CANCEL` 时取消原因必填；`NOT_PASS` 时评价或淘汰说明至少一项必填。

**`HrmRecruitInterviewRespVO`**：基础字段、候选人/职位摘要、面试官姓名、轮次、结果及名称、评价（按权限返回）、创建/更新时间。

### 4.3 Offer VO

**`HrmRecruitOfferCreateReqVO`**：`applicationId`、`salaryAmount`、`salaryUnit`、`entryPostId`、`plannedOnboardDate`、`validUntil`、`attachmentIds`、`remark`。

**`HrmRecruitOfferUpdateReqVO`**：`id` 加上述可编辑字段；仅 `DRAFT` 或 `REJECTED` 允许修改。

**`HrmRecruitOfferPageReqVO`**：`applicationId`、`candidateId`、`postId`、`approvalStatus`、`plannedOnboardDate[]`、`keyword`、分页字段。

**`HrmRecruitOfferBpmCallbackReqVO`**：`offerId`、`processInstanceId`、`approvalVersion`、`approvalResult`、`comment`、`callbackTime`、`requestId`。

**`HrmRecruitOfferOnboardReqVO`**：`offerId`、`confirmTime`、`employeeNo`（如由 HR 指定）、`requestId`。

**`HrmRecruitOfferRespVO`**：Offer 字段、候选人/职位摘要、审批信息、附件授权信息、`employeeId`、可执行操作集合。

## 5. 后端校验与事务要求

- 创建应聘：职位、候选人、简历、渠道均校验租户和存在性；锁定候选人+职位进行中记录，防止并发重复投递。
- 面试结果：同一轮只能结束一次；写入结果后由状态机服务计算应聘状态，不允许 Controller 直接更新状态。
- Offer 提交：仅 `PASSED` 应聘可提交；提交时保存字段快照和 `approval_version`。
- BPM 回调：以 `process_instance_id + approval_version` 幂等；重复回调直接返回成功但不重复推进状态。
- 转员工：以 `application_id` 加分布式锁/唯一业务键保证幂等；员工创建和应聘、Offer 回写采用本地事务或可补偿事务。

## 6. 待确认与落地顺序

待确认：重复投递状态集合、面试通过判定、Offer 有效期与审批节点、`entryPostId` 是否引用基座岗位、是否自动创建平台用户、历史面试迁移映射。

确认后按以下顺序落地：

1. 建立 DDL 迁移脚本和状态枚举。
2. 实现 application DO/Mapper/Service/Controller 及单元测试。
3. 将现有面试表和接口补充 `application_id`、`round_no`。
4. 实现 Offer 与 BPM 回调。
5. 接入员工建档，完成幂等转员工及联调验收。
