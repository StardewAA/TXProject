package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.approval;

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

import java.time.LocalDateTime;

/**
 * HRM 员工异动审批申请单 DO（手动审批，不依赖 Flowable）
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_approval")
@KeySequence("hrm_employee_approval_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeApprovalDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String jobNumber;
    /**
     * 审批类型：1 转正，2 调岗/晋升，3 离职
     */
    private Integer type;
    private String reason;
    private LocalDateTime effectTime;
    private String remark;
    /** 目标部门编号（调岗/晋升） */
    private Long targetDeptId;
    /** 目标岗位名称（调岗/晋升） */
    private String targetPostName;
    /** 目标岗位职级（调岗/晋升） */
    private String targetPostLevel;
    /** 目标直属上级员工编号（调岗/晋升） */
    private Long targetLeaderEmployeeId;
    /** 目标工作地址（调岗/晋升） */
    private String targetWorkAddress;
    /** 离职类型（对齐 HrmEmployeeQuitTypeEnum） */
    private Integer quitType;
    /** 离职原因（对齐 HrmEmployeeQuitReasonEnum） */
    private Integer quitReason;
    /** 薪资结算时间（离职） */
    private LocalDateTime salarySettlementTime;
    /** 目标基本工资（晋升/调岗填写后，审批通过自动套薪） */
    private java.math.BigDecimal targetBasicSalary;
    /** 目标绩效工资 */
    private java.math.BigDecimal targetPerformanceSalary;
    /**
     * 状态：1 待审批，2 已通过，3 已驳回
     */
    private Integer status;
    /**
     * 是否已生效
     */
    private Boolean applied;
    private String applicantName;
    private LocalDateTime applyTime;
    private String approverName;
    private LocalDateTime approveTime;
    private String approveComment;

}
