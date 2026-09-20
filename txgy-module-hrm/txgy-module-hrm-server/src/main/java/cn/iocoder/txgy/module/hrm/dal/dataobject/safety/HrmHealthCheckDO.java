package cn.iocoder.txgy.module.hrm.dal.dataobject.safety;

import cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

/**
 * HRM 体检与职业健康记录 DO
 *
 * @author OPENLAB BS
 */
@TableName(value = "hrm_health_check", autoResultMap = true)
@KeySequence("hrm_health_check_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmHealthCheckDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 员工编号
     */
    private Long employeeId;
    /**
     * 体检类型：1 入职，2 在岗，3 离职，4 职业健康
     */
    private Integer checkType;
    /**
     * 体检日期
     */
    private LocalDate checkDate;
    /**
     * 体检机构
     */
    private String institution;
    /**
     * 体检结论：1 合格，2 不合格，3 需复查
     */
    private Integer result;
    /**
     * 复查日期
     */
    private LocalDate recheckDate;
    /**
     * 有效期至（为空表示长期有效）
     */
    private LocalDate validUntil;
    /**
     * 体检报告附件
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 备注
     */
    private String remark;

}
