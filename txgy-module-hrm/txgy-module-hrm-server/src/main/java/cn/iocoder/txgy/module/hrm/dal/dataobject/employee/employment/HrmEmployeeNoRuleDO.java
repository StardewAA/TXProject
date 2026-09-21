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
 * HRM 工号生成规则 DO
 *
 * <p>规则：工号 = 租户名称缩写 + 顺序号（左补零），租户内独立计数、永不复用。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_no_rule")
@KeySequence("hrm_employee_no_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeNoRuleDO extends TenantBaseDO {

    /**
     * 规则编号
     */
    @TableId
    private Long id;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 租户缩写（工号前缀）
     */
    private String tenantAbbr;
    /**
     * 顺序号位数（左补零）
     */
    private Integer serialLength;
    /**
     * 前缀与序号之间的分隔符，默认空
     */
    private String prefixSeparator;
    /**
     * 重置周期：0 不重置，1 按年重置
     */
    private Integer resetPeriod;
    /**
     * 是否允许人工指定工号
     */
    private Boolean allowManual;
    /**
     * 状态：0 启用，1 停用
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
