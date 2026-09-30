package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment;

import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitReasonEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeStatusEnum;
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
import java.time.LocalDate;

/**
 * HRM 员工离职信息 DO
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_quit_info")
@KeySequence("hrm_employee_quit_info_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeQuitInfoDO extends TenantBaseDO {

    /**
     * 离职信息编号
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
     * 计划离职时间
     */
    private LocalDateTime planQuitTime;
    /**
     * 申请离职时间
     */
    private LocalDateTime applyQuitTime;
    /**
     * 薪资结算时间
     */
    private LocalDateTime salarySettlementTime;
    /**
     * 离职类型
     *
     * 枚举 {@link HrmEmployeeQuitTypeEnum}
     */
    private Integer type;
    /**
     * 离职原因
     *
     * 枚举 {@link HrmEmployeeQuitReasonEnum}
     */
    private Integer reason;
    /**
     * 备注
     */
    private String remark;
    /**
     * 离职前员工状态
     *
     * 枚举 {@link HrmEmployeeStatusEnum}
     */
    private Integer oldEmployeeStatus;
    /**
     * 交接状态
     *
     * 枚举 {@link cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverStatusEnum}
     */
    private Integer handoverStatus;
    /**
     * 交接人（员工编号）
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long handoverToEmployeeId;
    /**
     * 交接确认时间
     */
    private LocalDateTime handoverConfirmTime;
    /**
     * 交接说明（无需交接时记录原因）
     */
    private String handoverRemark;
    /**
     * 计薪截止日（默认等于离职生效日，可按结算周期提前封账）
     */
    private LocalDate salaryCutoffDate;
    /**
     * 工资是否已结清
     */
    private Boolean salarySettled;
    /**
     * 工资结清时间
     */
    private LocalDateTime salarySettleTime;
    /**
     * 结算说明
     */
    private String salarySettleRemark;

}
