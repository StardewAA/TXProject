# HRM 功能测试记录（2026-09-25 复测）

环境：租户秦川(2020) · 管理端 http://localhost:5173 · 网关 http://localhost:48080  
账号：qinchuan（超管）、qinchuan_hr（HR）  
执行：Playwright 全量 UI（`scripts/run-phase1-full.mjs`、`run-phase2-e2e.mjs`、`probe-sidebar-routes.mjs`）+ 代码走查（四分册）

结构化结论见 `test-results.md`；建议见 `improvements.md`；代码见 `code-map.md`。  
原始行级数据：`evidence/phase1-full.json`（323）、`evidence/phase2-e2e.json`（32）、`evidence/sidebar-routes.json`。

## 第一阶段

全菜单 59+ 路径 × 检查矩阵（加载/CRUD/查询/导入导出/审批/启停）→ 323 条记录。

**失败/异常摘要**：

| 路径 | 结果 | 要点 |
| --- | --- | --- |
| `/hrm/recruit/post` | 无草稿 | 保存即发布 |
| `/hrm/attendance/leave` | 无新增 | 仅导出/审批 |
| `/hrm/company` | 文案 | 「新 增」空格拆字 |
| `/hrm/salary/config/attendance-rule` | 404 | **测试误用**；正确 `/hrm/salary/attendance-rule` |
| `/hrm/salary/config/rate-limit` | 404 | **测试误用**；正确 `/hrm/salary/rate-limit` |
| `/hrm/portal/recruit` | 404 | 正确公开路由 `/portal/recruit` |
| 多列表空搜索 | 一般 | emptyHint 不足 |

侧边栏真实 href 与路由对照见 `code-map.md` §6、`evidence/sidebar-routes.json`。

## 第二阶段 · 主线 A

需求操作齐全；职位缺草稿；候选人漏斗/Offer 可用；入职衔接员工档案；看板可用。  
代码深挖：并发编制（Q-001）、Offer 有效期 send/confirm 不一致（Q-002）、需求状态无矩阵（Q-009）。

## 第二阶段 · 主线 B

组织/入职/合同/转正调岗/考勤/薪资/社保/离职主路径可用。  
缺口：管理端请假补录（Q-004）；核算就绪非阻断（Q-013）；合同 EXPIRED 不自动（Q-011）；月表可重复建（Q-012）。

## 异常路径

政策空表单必填拦截通过；非法详情路由无守卫（Q-016）。

## 权限

HR 可访问工作台/员工/工资/候选人/员工端/绩效（抽检通过）。  
代码面：门户简历/代理/投递缺注解（Q-005）；代理任务 IDOR（Q-006）；薪资社保无部门过滤（Q-007）。

## 衔接

招聘入职自动生成员工+工号+合同待签，无需重复建档。  
入职后薪资组/考勤组/社保仍人工配置 → 建议 I-020 入职向导。
