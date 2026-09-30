package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HRM 员工离职交接明细 DO
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_quit_handover_item")
@KeySequence("hrm_employee_quit_handover_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeQuitHandoverItemDO extends TenantBaseDO {

    /**
     * 交接明细编号
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
     * 物品类型
     *
     * 枚举 {@link HrmEmployeeQuitHandoverItemTypeEnum}
     */
    private Integer itemType;
    /**
     * 物品名称
     */
    private String itemName;
    /**
     * 数量
     */
    private BigDecimal quantity;
    /**
     * 单位
     */
    private String unit;
    /**
     * 交接状态
     *
     * 枚举 {@link HrmEmployeeQuitHandoverItemStatusEnum}
     */
    private Integer status;
    /**
     * 交回确认时间
     */
    private LocalDateTime confirmTime;
    /**
     * 备注
     */
    private String remark;

}
