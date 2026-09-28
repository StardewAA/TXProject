package cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config;

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

import java.math.BigDecimal;

/**
 * HRM 比例上限配置 DO
 *
 * <p>按「计薪项 / 参保项」分别配置比例上限：考勤计薪规则用 {@link #rateLimit}，
 * 参保项目用 {@link #corporateRateLimit} 与 {@link #personalRateLimit}。
 * 保存时按本表校验，避免把上限写死在代码或前端里。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_salary_rate_limit")
@KeySequence("hrm_salary_rate_limit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmSalaryRateLimitDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 业务类型：1 考勤计薪规则 2 参保方案
     *
     * 枚举 {@link cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryRateLimitBizTypeEnum}
     */
    private Integer bizType;
    /**
     * 计薪项 / 参保项编码
     *
     * <p>考勤计薪项为规则字段名（如 absenceDeductRate）；
     * 参保项为项目类型编码（如 1 养老保险、10 公积金）。</p>
     */
    private String itemCode;
    /**
     * 计薪项 / 参保项名称
     */
    private String itemName;
    /**
     * 比例上限（%），考勤计薪项使用
     */
    private BigDecimal rateLimit;
    /**
     * 公司缴纳比例上限（%），参保项目使用
     */
    private BigDecimal corporateRateLimit;
    /**
     * 个人缴纳比例上限（%），参保项目使用
     */
    private BigDecimal personalRateLimit;
    /**
     * 状态：0 启用，1 停用（停用表示不做上限校验）
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
