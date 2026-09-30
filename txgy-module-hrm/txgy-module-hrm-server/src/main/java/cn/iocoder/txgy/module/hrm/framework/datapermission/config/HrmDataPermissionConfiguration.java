package cn.iocoder.txgy.module.hrm.framework.datapermission.config;

import cn.iocoder.txgy.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.iocoder.txgy.module.hrm.dal.dataobject.attendance.record.HrmAttendanceClockDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.attendance.record.HrmAttendanceLeaveDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateRenewalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateWarningDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.approval.HrmEmployeeApprovalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitHandoverItemDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitInfoDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeSalaryCardDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeEducationExperienceDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeTrainingExperienceDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeWorkExperienceDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount.HrmDeptHeadcountDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeContactDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeFileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeePersonalNoteDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.performance.assessment.HrmPerformanceAssessmentActionRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.performance.assessment.HrmPerformanceAssessmentAppealRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.performance.assessment.HrmPerformanceAssessmentDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * HRM 数据权限配置（阶段二 2.1）
 *
 * <p>产品口径：<b>HR 与董事长（角色数据范围为「全部数据」）可以查看全部员工；
 * 各部门管理（角色数据范围＝自定义部门/本部门及以下）只能查看自己部门的员工；
 * 普通员工（数据范围＝仅本人）只能查看本人。</b></p>
 *
 * <p>框架侧说明：注册表之后即对其上所有查询默认生效（{@code @DataPermission} 注解默认开启），
 * 具体过滤条件由登录用户所属角色的数据范围决定：全部 → 不加条件；部门 → {@code dept_id IN (...)}；
 * 仅本人 → {@code user_id = 当前用户}；无登录用户（定时任务、匿名门户）→ 不加条件。</p>
 *
 * <p>注册范围：员工主档与用工需求单/部门编制（原生 dept_id），以及按人（employee_id）的业务表——
 * 后者通过迁移脚本 {@code sql/2026-09-22-hrm-dept-id-for-member-tables.sql} 冗余 dept_id 列并回填，
 * 新建记录由 BEFORE INSERT 触发器自动从员工带出部门（员工调岗不追溯历史，与框架口径一致）。</p>
 *
 * <p>刻意不注册：薪资（hrm_salary_*）、社保（hrm_insurance_*）、招聘（hrm_recruit_*，
 * 含职位/候选人/应聘/面试/Offer/沟通）、个人简历（hrm_personal_resume*）、
 * 员工黑名单（全公司风控清单，隔离会导致跨部门误录用）。</p>
 *
 * @author OPENLAB BS
 */
@Configuration(value = "hrmDataPermissionConfiguration", proxyBeanMethods = false)
public class HrmDataPermissionConfiguration {

    @Bean
    public DeptDataPermissionRuleCustomizer hrmDeptDataPermissionRuleCustomizer() {
        return rule -> {
            // 员工主档：按所属部门做数据隔离；「仅本人」范围时按绑定用户过滤
            rule.addDeptColumn(HrmEmployeeDO.class);
            rule.addUserColumn(HrmEmployeeDO.class, "user_id");
            // 用工需求单与部门编制同样按部门归属隔离
            rule.addDeptColumn(HrmRecruitDemandDO.class);
            rule.addDeptColumn(HrmDeptHeadcountDO.class);
            // 考勤
            rule.addDeptColumn(HrmAttendanceClockDO.class);
            rule.addDeptColumn(HrmAttendanceLeaveDO.class);
            // 绩效
            rule.addDeptColumn(HrmPerformanceAssessmentDO.class);
            rule.addDeptColumn(HrmPerformanceAssessmentActionRecordDO.class);
            rule.addDeptColumn(HrmPerformanceAssessmentAppealRecordDO.class);
            // 合同 / 证件 / 安全 / 健康
            rule.addDeptColumn(HrmEmployeeContractDO.class);
            rule.addDeptColumn(HrmContractSignRecordDO.class);
            rule.addDeptColumn(HrmEmployeeCertificateDO.class);
            rule.addDeptColumn(HrmCertificateWarningDO.class);
            rule.addDeptColumn(HrmCertificateRenewalDO.class);
            rule.addDeptColumn(HrmSafetyEducationDO.class);
            rule.addDeptColumn(HrmHealthCheckDO.class);
            // 异动 / 离职 / 离职交接 / 员工审批
            rule.addDeptColumn(HrmEmployeeChangeRecordDO.class);
            rule.addDeptColumn(HrmEmployeeQuitInfoDO.class);
            rule.addDeptColumn(HrmEmployeeQuitHandoverItemDO.class);
            rule.addDeptColumn(HrmEmployeeApprovalDO.class);
            // 员工子档案（跟随员工主档的部门）
            rule.addDeptColumn(HrmEmployeeContactDO.class);
            rule.addDeptColumn(HrmEmployeeEducationExperienceDO.class);
            rule.addDeptColumn(HrmEmployeeWorkExperienceDO.class);
            rule.addDeptColumn(HrmEmployeeTrainingExperienceDO.class);
            rule.addDeptColumn(HrmEmployeeFileDO.class);
            rule.addDeptColumn(HrmEmployeePersonalNoteDO.class);
            rule.addDeptColumn(HrmEmployeeSalaryCardDO.class);
        };
    }

}
