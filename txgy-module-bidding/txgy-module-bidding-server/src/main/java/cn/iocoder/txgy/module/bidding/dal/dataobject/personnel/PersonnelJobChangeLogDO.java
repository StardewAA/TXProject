package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 人员受聘企业变更流水（表 personnel_job_change_log）。
 *
 * <p>规范出处：用户业务口径"人员 12 个月内更换受聘企业 ≥4 次，打一年异常标签"；
 * 《资质管理/06-…-2026-09-10.md》§二-2；《电子资质七大模块技术方案.md》§1.1(d)。
 * <p>为什么单独记录流水而不是只看员工档案的"当前企业"：频繁变更的判定依赖**变更历史**，
 * 只保留当前企业会让"今年换了 5 家"这一事实在每次变更后被覆盖，永远算不出来。
 * <p>业务目标：挂证/职业化挂靠人员可被自动识别。
 */
@TableName("personnel_job_change_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelJobChangeLogDO extends TenantBaseDO {

    private Long id;
    private Long personId;
    /** 原受聘企业（首次入职为空） */
    private Long fromEntId;
    /** 新受聘企业 */
    private Long toEntId;
    /** 类型 code：TRANSFER 变更单位/RESIGN 离职/REHIRE 复职 */
    private String changeType;
    private LocalDate effectiveDate;
    private String sourceRef;
    private String remark;
}
