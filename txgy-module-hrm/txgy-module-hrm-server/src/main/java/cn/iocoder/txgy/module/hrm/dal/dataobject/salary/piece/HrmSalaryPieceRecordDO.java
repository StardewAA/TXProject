package cn.iocoder.txgy.module.hrm.dal.dataobject.salary.piece;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
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

/**
 * HRM 计件计量明细 DO
 *
 * <p>一线工人按工程量计酬（㎡ / 吨 / 延米），一个工人一个月可能有多道工序、多个单价；
 * 核算工资表时把当月明细的金额汇总进「计件工资」薪资项。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_salary_piece_record")
@KeySequence("hrm_salary_piece_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmSalaryPieceRecordDO extends TenantBaseDO {

    /**
     * 明细编号
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
     * 年
     */
    private Integer year;
    /**
     * 月
     */
    private Integer month;
    /**
     * 工程量 / 工序名称
     */
    private String itemName;
    /**
     * 单位
     */
    private String unit;
    /**
     * 完成工程量
     */
    private BigDecimal quantity;
    /**
     * 计件单价
     */
    private BigDecimal unitPrice;
    /**
     * 计件金额（后端按 工程量 × 单价 计算）
     */
    private BigDecimal amount;
    /**
     * 备注
     */
    private String remark;

}
