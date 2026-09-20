package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment;

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

/**
 * HRM 工号序号 DO
 *
 * <p>每个「租户 + 规则 + 周期」一行，通过行锁实现并发安全取号。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_no_sequence")
@KeySequence("hrm_employee_no_sequence_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeNoSequenceDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 规则编号
     */
    private Long ruleId;
    /**
     * 周期键：ALL 或年份
     */
    private String periodKey;
    /**
     * 当前已使用的序号
     */
    private Long currentValue;

}
