package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment;

import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeReasonEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeTypeEnum;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
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
 * HRM 员工异动记录 DO
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_change_record")
@KeySequence("hrm_employee_change_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeChangeRecordDO extends TenantBaseDO {

    /**
     * 异动记录编号
     */
    @TableId
    private Long id;
    /**
     * 员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long employeeId;
    /**
     * 异动类型
     *
     * 枚举 {@link HrmEmployeeChangeTypeEnum}
     */
    private Integer type;
    /**
     * 异动原因
     *
     * 枚举 {@link HrmEmployeeChangeReasonEnum}
     */
    private Integer reason;
    /**
     * 原部门编号
     *
     * 关联 {@link DeptRespDTO#getId()}
     */
    private Long oldDeptId;
    /**
     * 新部门编号
     *
     * 关联 {@link DeptRespDTO#getId()}
     */
    private Long newDeptId;
    /**
     * 原岗位
     */
    private String oldPostName;
    /**
     * 新岗位
     */
    private String newPostName;
    /**
     * 原职级
     */
    private String oldPostLevel;
    /**
     * 新职级
     */
    private String newPostLevel;
    /**
     * 原工作地点
     */
    private String oldWorkAddress;
    /**
     * 新工作地点
     */
    private String newWorkAddress;
    /**
     * 原直属上级员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long oldLeaderEmployeeId;
    /**
     * 新直属上级员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long newLeaderEmployeeId;
    /**
     * 试用期，单位：月
     */
    private Integer probation;
    /**
     * 生效日期
     */
    private LocalDateTime effectTime;
    /**
     * 实际生效时间
     */
    private LocalDateTime appliedTime;
    /**
     * 异动来源
     *
     * 枚举 {@link HrmEmployeeChangeTypeEnum} 同包下的 {@code HrmEmployeeChangeSourceEnum}
     */
    private Integer source;
    /**
     * 经办人（系统用户编号）
     */
    private Long operatorUserId;
    /**
     * 经办人姓名
     */
    private String operatorName;
    /**
     * 异动审批单编号
     *
     * 关联 {@code HrmEmployeeApprovalDO#getId()}
     */
    private Long approvalId;
    /**
     * 审批人（系统用户编号）
     */
    private Long approverUserId;
    /**
     * 审批人姓名
     */
    private String approverName;
    /**
     * 审批通过时间
     */
    private LocalDateTime approveTime;
    /**
     * 撤销状态
     *
     * 枚举 {@code HrmEmployeeChangeCancelStatusEnum}：0 正常 1 已撤销
     */
    private Integer cancelStatus;
    /**
     * 撤销原因
     */
    private String cancelReason;
    /**
     * 撤销时间
     */
    private LocalDateTime cancelTime;
    /**
     * 撤销人（系统用户编号）
     */
    private Long cancelUserId;
    /**
     * 撤销人姓名
     */
    private String cancelUserName;
    /**
     * 备注
     */
    private String remark;

}
