# HRM 部门数据权限范围决策说明（Q-007 / I-012）

> 结论先行：**当前不把薪资（hrm_salary_*）、社保（hrm_insurance_*）、招聘（hrm_recruit_*）、
> 个人简历（hrm_personal_resume*）、员工黑名单纳入部门数据权限**；这是产品口径的**有意选择**，
> 不是遗漏。若后续合规要求按部门隔离，按本文「启用步骤」实施。

## 1. 现状（代码事实）

- 配置位置：`txgy-module-hrm/.../framework/datapermission/config/HrmDataPermissionConfiguration.java`
- 已注册（按部门隔离）：员工主档、用工需求单、部门编制、考勤、绩效、合同/证件/安全/健康、异动/离职/交接、员工子档案。
- 刻意未注册：薪资、社保、招聘（职位/候选人/应聘/面试/Offer/沟通）、个人简历、员工黑名单。

## 2. 为什么刻意不隔离

| 场景 | 若按部门隔离会发生的后果 |
| --- | --- |
| 薪资 | 薪酬本就最小可见（另由菜单/按钮权限控制），再叠加部门会让跨部门核算、公司汇总、档案归档出现"查不到数据" |
| 社保 | 社保申报按公司统一办理，跨部门汇总后再申报，隔离会造成申报表缺人 |
| 招聘 | 候选人/职位是**全公司共享池**（门户投递、跨部门调配），隔离会导致候选人被"看不见"造成重复沟通 |
| 个人简历 | 归属人本人，已由「登录用户 = 归属人」在服务层强校验，无需部门维度 |
| 员工黑名单 | 全公司风控清单，隔离会导致跨部门误录用 |

## 3. 风险与缓解

- 剩余风险：拥有「薪资查看」权限的部门 HR 可以看到**全公司**薪资数据。
- 现有缓解：菜单 + 按钮权限（`hrm:salary:*`）控制到"能不能进这个功能"，审计日志记录到人。
- 如果合规要求"部门 HR 只能看本部门薪资"，请按第 4 节启用隔离，不要仅依赖权限菜单。

## 4. 启用步骤（如果产品决定隔离）

1. **补 dept_id 列**：为 `hrm_salary_month_record`、`hrm_salary_month_employee_record`、
   `hrm_salary_employee_info`、`hrm_insurance_month_record`、`hrm_insurance_month_employee_record`、
   `hrm_recruit_post`、`hrm_recruit_application` 等表补 `dept_id` 并回填（可参考
   `sql/2026-09-22-hrm-dept-id-for-member-tables.sql` 的写法：冗余列 + BEFORE INSERT 触发器）。
2. **注册规则**：在 `HrmDataPermissionConfiguration` 中对上述 DO 调 `rule.addDeptColumn(...)`。
3. **回归验证**：分别用「全部数据 / 本部门 / 仅本人」三种数据范围的角色登录，
   核对薪资表列表、核算、发放、社保申报、招聘漏斗的数据条数是否符合预期。
4. **注意汇总口径**：公司级汇总（首页 KPI、工资表汇总、社保申报）需要 `@DataPermission(enable = false)`
   或由具备「全部数据」范围的角色执行，否则数字会变小。

## 5. 决策记录

- 决策人：业务侧（HR 负责人）确认按「全公司可见 + 功能权限控制」口径执行。
- 复核时机：合规审查、或出现"部门 HR 看到非本部门薪资"的诉求时。
