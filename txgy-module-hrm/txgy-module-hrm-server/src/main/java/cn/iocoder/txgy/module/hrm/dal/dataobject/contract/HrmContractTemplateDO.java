package cn.iocoder.txgy.module.hrm.dal.dataobject.contract;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmTemplateCategoryEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum;
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
 * HRM 模板 DO
 *
 * <p>按分类归集各类模板文件：劳动合同、个人电子档案、培训协议。
 * 合同类模板供合同生成与导出时按 ${变量} 渲染；档案/培训类模板供下载与套打。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_contract_template")
@KeySequence("hrm_contract_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmContractTemplateDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 模板分类：1 劳动合同，2 个人电子档案，3 培训协议
     *
     * 枚举 {@link HrmTemplateCategoryEnum}
     */
    private Integer category;
    /**
     * 合同类型
     *
     * 枚举 {@link HrmEmployeeContractTypeEnum}
     * 仅「劳动合同」分类的模板使用；档案/培训类模板为空
     */
    private Integer contractType;
    /**
     * 适用用工形式
     */
    private String employType;
    /**
     * 适用用工主体编号（hrm_company_profile.id）；为空表示不限用工主体
     */
    private Long employerId;
    /**
     * 版本号
     */
    private Integer version;
    /**
     * 状态：0 启用，1 停用
     */
    private Integer status;
    /**
     * 是否该类型默认模板
     */
    private Boolean defaultStatus;
    /**
     * 显示顺序（同分类内排序）
     */
    private Integer sort;
    /**
     * 来源目录（批量导入时保留，便于追溯）
     */
    private String sourceDir;
    /**
     * 模板文件地址（docx）
     */
    private String fileUrl;
    /**
     * 缩略图地址（PNG，列表卡片展示）
     */
    private String thumbnailUrl;
    /**
     * 预览文件地址（PDF，浏览器内嵌预览）
     */
    private String previewUrl;
    /**
     * 模板文件 SHA-256
     */
    private String fileHash;
    /**
     * 模板变量清单（逗号分隔，如 ${companyName},${employeeName}）
     */
    private String variables;
    /**
     * 本次变更说明
     */
    private String changeNote;
    /**
     * 变更人编号
     */
    private Long changeUserId;
    /**
     * 变更时间
     */
    private java.time.LocalDateTime changeTime;
    /**
     * 备注
     */
    private String remark;

}
