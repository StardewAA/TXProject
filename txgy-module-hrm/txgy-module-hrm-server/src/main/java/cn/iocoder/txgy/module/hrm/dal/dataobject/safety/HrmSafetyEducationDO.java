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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HRM 三级安全教育 DO
 *
 * @author OPENLAB BS
 */
@TableName(value = "hrm_safety_education", autoResultMap = true)
@KeySequence("hrm_safety_education_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmSafetyEducationDO extends TenantBaseDO {

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
     * 教育级别：1 公司级，2 项目级，3 班组级
     */
    private Integer level;
    /**
     * 教育日期
     */
    private LocalDate educationDate;
    /**
     * 教育人
     */
    private String educatorName;
    /**
     * 学时
     */
    private BigDecimal hours;
    /**
     * 教育内容
     */
    private String content;
    /**
     * 考核成绩
     */
    private BigDecimal examScore;
    /**
     * 是否合格
     */
    private Boolean qualified;
    /**
     * 签字表/试卷/照片
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 备注
     */
    private String remark;

}
