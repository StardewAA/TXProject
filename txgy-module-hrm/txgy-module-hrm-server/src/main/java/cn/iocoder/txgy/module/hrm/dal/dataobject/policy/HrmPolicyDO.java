package cn.iocoder.txgy.module.hrm.dal.dataobject.policy;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * HRM 政策库 DO。
 *
 * <p>社保 / 公积金 / 劳动合同 / 培训等政策制度，供社保方案、合同模板等关联引用。</p>
 */
@TableName(value = "hrm_policy", autoResultMap = true)
@KeySequence("hrm_policy_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPolicyDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 政策名称 */
    private String name;
    /** 类别：social_insurance / provident_fund / labor_contract / training / other */
    private String category;
    /** 文号 */
    private String docNo;
    /** 发布机关 */
    private String publishOrg;
    /** 发布日期 */
    private LocalDate publishDate;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expireDate;
    /** 适用范围：group / tenant / region */
    private String scope;
    /** 范围明细（地区名称等） */
    private String scopeValue;
    /** 政策正文 */
    private String content;
    /** 附件地址 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileUrls;
    /** 标签 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    /** 状态：0 启用 1 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
