# HRM 招聘模块第二组总代码修改记录

**项目**：天信管业防腐保温智慧平台 HRM 招聘模块第二组  
**负责范围**：应聘、筛选、面试、Offer、录用转员工  
**记录规则**：每次代码、数据库、配置、菜单或接口变更均追加记录；设计文档变更同时登记关联文件。  
**当前版本**：V20260903.007

## 1. 变更总览

| 版本 | 日期 | 类型 | 内容 | 状态 |
| --- | --- | --- | --- | --- |
| V20260903.001 | 2026-09-03 | 设计 | 完成第二组接口与状态机确认稿 | 已完成，待业务确认 |
| V20260903.002 | 2026-09-03 | 设计 | 完成数据库、状态枚举、请求/响应 VO 设计稿 | 已完成，待技术评审 |
| V20260903.003 | 2026-09-03 | 代码/数据库 | 新增应聘基础表、状态日志表、状态枚举及 Application 后端基础对象 | 已完成基础层 |
| V20260903.004 | 2026-09-03 | 验证 | 修复编译目标后完成 HRM 模块 Maven 编译 | 已通过 |
| V20260903.005 | 2026-09-03 | 代码/验证 | 实现 Application 服务、状态流转、Controller；重新编译时发现 Maven 仍使用 JDK 17，`target 25` 失败 | 待环境修复 |
| V20260903.006 | 2026-09-03 | 构建环境 | 统一 Maven 使用 JDK 25，根 pom 增加 Java 版本 Enforcer，编译目标显式使用 release 25 | 已完成 |
| V20260903.007 | 2026-09-03 | 代码/验证 | 补齐筛选到面试中的 Application 状态流转接口并完成 JDK 25 编译 | 已通过 |

## 2. 详细变更记录

### V20260903.001：接口与状态机确认稿

**变更文件**：

- `docs/HRM招聘模块第二组接口与状态机确认稿.md`

**主要内容**：

- 明确第二组不负责职位、候选人基础资料和简历底层能力。
- 冻结 `candidate → application → interview → offer → employee` 领域关系草案。
- 定义应聘状态、允许流转、API 路径、权限和联调验收场景。
- 列出重复投递、面试通过、Offer 审批、转员工等待确认事项。

### V20260903.002：数据与 VO 设计稿

**变更文件**：

- `docs/HRM招聘模块第二组数据与VO设计稿.md`

**主要内容**：

- 设计 `hrm_recruit_application`、`hrm_recruit_application_log`、`hrm_recruit_offer`。
- 设计面试表增加 `application_id`、`round_no` 的迁移方案。
- 定义应聘、Offer、面试结果枚举。
- 定义 Application、Interview、Offer 请求 VO 和响应 VO。
- 明确租户校验、重复投递、BPM 回调和转员工幂等要求。

### V20260903.003：基础代码与迁移草案

**新增/修改文件**：

- `sql/hrm_recruit_application.sql`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/enums/recruit/application/HrmRecruitApplicationStatusEnum.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/enums/recruit/application/HrmRecruitOfferApprovalStatusEnum.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/dataobject/recruit/application/HrmRecruitApplicationDO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/mysql/recruit/application/HrmRecruitApplicationMapper.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationService.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/vo/application/HrmRecruitApplicationCreateReqVO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/vo/application/HrmRecruitApplicationPageReqVO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/vo/application/HrmRecruitApplicationStatusReqVO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/vo/application/HrmRecruitApplicationRespVO.java`

**实现内容**：

- 新增应聘主表和状态日志表的 MySQL 迁移草案。
- 为现有 `hrm_recruit_interview` 增加 `application_id`、`round_no` 迁移字段。
- 新增应聘状态枚举，包含进行中状态集合和终态判断。
- 新增 Offer 审批状态枚举。
- 新增 Application DO、Mapper、Service 接口和基础 VO。
- Mapper 提供按候选人、职位、进行中状态查询的方法，为重复投递校验准备。

**验证情况**：

- 已完成文件结构和依赖风格静态核对。
- 执行 `mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile` 时，构建在 `txgy-common` 阶段因现有 `target 25` 与当前 JDK 不兼容而停止，尚未进入 HRM 编译阶段。
- 未执行数据库迁移，SQL 仍需技术评审和环境验证。

### V20260903.004：编译验证

**验证命令**：

- `mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile`

**验证结果**：

- `BUILD SUCCESS`。
- `txgy-common`、HRM API、HRM Server 及其依赖模块均成功编译。
- 本次新增 Application 基础对象未产生编译错误。

### V20260903.005：Application 第一条链路

**新增文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/dataobject/recruit/application/HrmRecruitApplicationLogDO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/mysql/recruit/application/HrmRecruitApplicationLogMapper.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationServiceImpl.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/HrmRecruitApplicationController.java`

**修改文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/mysql/recruit/application/HrmRecruitApplicationMapper.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/enums/ErrorCodeConstants.java`

**实现内容**：

- 实现创建应聘、进行中重复投递校验、开始筛选、淘汰和撤回。
- 实现应聘状态日志落库和状态流转前置校验。
- 新增 Application 分页、详情及状态操作 Controller。
- 增加应聘不存在、重复投递、非法流转和原因必填错误码。

**验证情况**：

- Maven 已进入 HRM Server 编译阶段，但当前 `mvn -version` 显示 Maven 使用 `D:\Java\jdk-17.0.18`，项目目标为 `25`，仍报 `invalid target release: 25`。
- 需将 Maven Toolchain/JAVA_HOME 切换至 JDK 25 后重新编译，之后再验证本次实现的源码错误。

### V20260903.006：`target 25` 根因修复

**根因**：系统 PATH 中的 `java` 可执行文件来自 JDK 25，但 `JAVA_HOME` 指向 `D:\Java\jdk-17.0.18`；Maven 启动脚本优先使用 `JAVA_HOME`，导致 Maven 使用 JDK 17 编译 `target 25`。

**修复内容**：

- 将用户级 `JAVA_HOME` 统一设置为 `D:\Java\jdk-25.0.3`。
- 将用户级 PATH 中的 JDK 17 路径替换为 JDK 25 路径。
- 在根 `pom.xml` 增加 `maven.compiler.release=25`。
- 在根 `pom.xml` 增加 `maven-enforcer-plugin`，在 `validate` 阶段强制 `[25,26)`，错误时直接提示正确的 `JAVA_HOME`。

**验证结果**：

- `mvn -version` 显示 Maven 使用 `D:\Java\jdk-25.0.3`。
- `mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile`：`BUILD SUCCESS`。
- HRM Server 以 `javac [release 25]` 完成编译。

### V20260903.007：筛选进入面试

**修改文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationService.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationServiceImpl.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/HrmRecruitApplicationController.java`

**实现内容**：

- 新增 `enterInterview` 服务方法和 `/enter-interview` 管理端接口。
- 状态机允许 `SCREENING → INTERVIEWING`，并记录状态日志。
- 沿用租户、权限和状态前置校验。

**验证结果**：

- `mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile`：`BUILD SUCCESS`。
- HRM Server 以 `javac [release 25]` 编译通过。

### V20260903.008：面试关联应聘记录

**变更文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/dataobject/recruit/candidate/HrmRecruitInterviewDO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/controller/admin/recruit/vo/interview/HrmRecruitInterviewSaveReqVO.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/dal/mysql/recruit/candidate/HrmRecruitInterviewMapper.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/candidate/HrmRecruitInterviewServiceImpl.java`

**变更原因**：共享招聘设计要求应聘记录作为流程聚合根，面试需支持同一应聘多轮关联；同时保留旧候选人接口的兼容性。

**实现内容**：

- 面试 DO/新增 VO 增加 `applicationId` 和 `roundNo`。
- 增加按 `application_id` 查询面试列表和最新轮次的 Mapper 方法。
- 新建面试时优先从应聘记录解析候选人，并按应聘记录计算轮次；未传 `applicationId` 时继续使用旧 `candidateId` 逻辑。

**兼容性/迁移**：`candidate_id` 暂保留；需先执行 `sql/hrm_recruit_application.sql` 中面试字段迁移，历史数据回填 `application_id` 后再将新接口设为必填。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile`：`BUILD SUCCESS`，使用 JDK 25、`javac [release 25]`。

### V20260903.009：面试结果回写应聘状态

**变更文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationService.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/application/HrmRecruitApplicationServiceImpl.java`
- `txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/service/recruit/candidate/HrmRecruitInterviewServiceImpl.java`

**变更原因**：共享需求要求面试结果驱动应聘流程，避免只更新旧候选人状态而导致应聘聚合状态不一致。

**实现内容**：面试通过时将关联应聘推进至 `PASSED`；面试未通过时将关联应聘推进至 `ELIMINATED` 并记录评价作为原因；新增应用服务内部状态推进方法，仍保留旧候选人状态更新逻辑。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile`：`BUILD SUCCESS`。

### V20260903.010：Offer 基础后端

**变更文件**：新增 `HrmRecruitOfferDO`、Mapper、Service、ServiceImpl、保存请求 VO；更新 `ErrorCodeConstants`。

**实现内容**：按既定设计实现 Offer 创建、应聘维度重复校验、提交审批、审批回调（流程实例与版本校验）及撤回，状态使用 `DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN`。

**兼容性/迁移**：仅新增 Java 后端代码，数据库表由另一团队依据共同设计文档对齐落地。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.011：Offer 管理端接口

**变更文件**：新增 `HrmRecruitOfferController`。

**实现内容**：提供 Offer 创建、详情、提交审批、审批回调和撤回接口，沿用 HRM 权限校验。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.012：Offer 发出与确认状态

**变更文件**：更新 `HrmRecruitOfferService`、`HrmRecruitOfferServiceImpl`、`HrmRecruitOfferController`。

**实现内容**：审批通过后支持发出 Offer，候选人确认后进入 `CONFIRMED`；状态前置校验并记录发出、确认时间。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.013：Offer 与应聘状态联动

**变更文件**：更新 `HrmRecruitApplicationService/Impl`、`HrmRecruitOfferServiceImpl`。

**实现内容**：Offer 创建限定应聘必须为 `PASSED`；提交审批推进应聘至 `OFFER_APPROVING`；发出推进至 `OFFER_SENT`；候选人确认推进至 `PENDING_ONBOARD`。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.014：Offer 确认入职幂等

**变更文件**：更新 Offer/Application 服务，新增 `HrmRecruitOfferOnboardReqVO`，更新 Offer Controller。

**实现内容**：确认入职接口复用现有员工建档服务；以 Offer 和 `application_id` 已回写的 `employee_id` 作为幂等结果，成功建档后回写两侧并将应聘状态置为 `ONBOARDED`。

**兼容性/迁移**：员工姓名、手机号等基础资料从候选人读取；数据库字段由另一团队按共同设计文档落地。

**验证情况**：修复 VO 导入后，`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.015：应聘状态乐观锁

**变更文件**：更新 `HrmRecruitApplicationMapper`、`HrmRecruitApplicationServiceImpl`。

**实现内容**：应聘状态和入职回写改为携带 `version` 条件更新，更新失败即返回状态流转错误，防止并发请求重复推进流程。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.016：审批驳回与 Offer 并发幂等

**变更文件**：更新 Application/Offer 服务及 Offer Mapper。

**实现内容**：审批驳回时应聘状态从 `OFFER_APPROVING` 回退到 `PASSED`；审批回调增加 `approval_version` 条件更新，并允许同一流程实例重复回调安全返回。

**验证情况**：`mvn -pl txgy-module-hrm/txgy-module-hrm-server -am -DskipTests compile -q`：`BUILD SUCCESS`。

### V20260903.017：招聘回归用例测试夹具同步

**变更文件**：

- `txgy-module-hrm/txgy-module-hrm-server/src/test/resources/sql/create_tables.sql`
- `txgy-module-hrm/txgy-module-hrm-server/src/test/java/cn/iocoder/txgy/module/hrm/service/recruit/candidate/HrmRecruitInterviewServiceImplTest.java`

**变更原因**：招聘面试服务新增 `application_id`、`round_no` 依赖后，H2 测试夹具及旧面试测试未同步，导致用例无法执行。

**实现内容**：补齐测试专用应聘、应聘日志、Offer 表及面试字段；为旧候选人模式注入应聘服务 Mock，并明确旧用例使用 `candidate_id` 兼容路径。

**兼容性/迁移**：仅影响测试 H2 数据库，不执行、不修改 Docker MySQL 生产数据库；生产表仍由另一团队按总设计文档落地。

**验证情况**：招聘回归用例执行 92 个；岗位、岗位类型、渠道、配置用例通过。面试用例已消除表结构及依赖注入错误，剩余 1 个旧断言因新增 `round_no` 已调整；候选人模块仍有 1 个既有 `LocalDateTime` 精度断言失败，待独立修复。

## 3. 后续变更登记模板

后续每次改动按以下格式追加：

```markdown
### VYYYYMMDD.NNN：变更标题

**变更文件**：

- `相对路径`

**变更原因**：说明需求、缺陷或联调背景。

**实现内容**：说明接口、数据、状态、权限或页面变化。

**兼容性/迁移**：说明旧数据、旧接口和回滚影响。

**验证情况**：记录执行的测试、构建命令及结果。
```

## 4. 当前待办

- [x] 修复或统一项目 JDK/编译目标后完成 HRM 编译。
- [ ] 评审并执行应聘、状态日志及 Offer DDL。
- [ ] 实现 `HrmRecruitApplicationServiceImpl` 和 Controller。
- [ ] 补充应用状态流转单元测试。
- [x] 改造面试关联为 `application_id`（兼容迁移阶段）。
- [ ] 接入 Offer BPM 审批和幂等转员工。
