package cn.iocoder.txgy.module.cms.dal.dataobject.Quota;

import cn.iocoder.txgy.module.cms.dal.dataobject.CmsBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资质定额资料库 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_quota", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQuotaDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long quotaId;
    /**
     * 资料名称
     */
    private String title;
    /**
     * 类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板
     */
    private Integer quotaType;
    /**
     * 适用地区
     */
    private String region;
    /**
     * 发布时间
     */
    private LocalDate publishDate;
    /**
     * 有效期至
     */
    private LocalDate validDate;
    /**
     * 文件附件ID
     */
    private Long fileId;
    /**
     * 说明备注
     */
    private String description;
    /**
     * 是否免费：0付费 1免费
     */
    private Integer isFree;
    /**
     * 审核状态
     */
    private Integer auditStatus;
    /**
     * 审核备注（驳回原因）
     */
    private String auditRemark;
    /**
     * 发布状态
     */
    private Integer publishStatus;
    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
