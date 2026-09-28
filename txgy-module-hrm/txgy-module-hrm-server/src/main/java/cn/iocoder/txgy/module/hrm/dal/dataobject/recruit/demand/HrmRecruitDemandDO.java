package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

/**
 * HRM 用工需求单 DO
 *
 * <p>场景：项目部 / 用工部门提出"要几个人、什么工种、要求什么证照、什么时候进场"，
 * HR 按需求建招聘职位，录用后回写到岗人数，到齐即完成。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_recruit_demand")
@KeySequence("hrm_recruit_demand_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmRecruitDemandDO extends TenantBaseDO {

    /**
     * 需求编号
     */
    @TableId
    private Long id;
    /**
     * 需求单号
     */
    private String demandNo;
    /**
     * 需求名称
     */
    private String demandName;
    /**
     * 用工部门 / 项目部编号
     */
    private Long deptId;
    /**
     * 用工主体编号
     *
     * 关联 {@link cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO#getId()}
     */
    private Long companyProfileId;
    /**
     * 工种（如：防腐工、保温工、喷砂工、探伤工、辅工）
     */
    private String workType;
    /**
     * 证照要求（多个用、分隔，如：高处作业证、焊工证）
     */
    private String certRequire;
    /**
     * 需求人数
     */
    private Integer headcount;
    /**
     * 已到岗人数
     */
    private Integer onboardCount;
    /**
     * 计划进场日期
     */
    private LocalDate planEntryDate;
    /**
     * 参考单价（计件/日薪，便于招聘时谈价）
     */
    private String expectSalary;
    /**
     * 紧急程度
     *
     * 枚举 {@link cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandUrgencyEnum}
     */
    private Integer urgency;
    /**
     * 状态
     *
     * 枚举 {@link cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandStatusEnum}
     */
    private Integer status;
    /**
     * 提出人
     */
    private String applicant;
    /**
     * 联系方式
     */
    private String contactMobile;
    /**
     * 备注
     */
    private String remark;

}
