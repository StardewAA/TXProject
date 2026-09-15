package cn.iocoder.txgy.module.cms.dal.dataobject.Policy;

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
 * 政策资讯 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_policy", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsPolicyDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long policyId;
    /**
     * 政策标题
     */
    private String title;
    /**
     * 层级：1国家 2省 3市 4区县
     */
    private Integer policyLevel;
    /**
     * 发布部门
     */
    private String publishDept;
    /**
     * 主题分类
     */
    private String category;
    /**
     * 效力状态：现行有效/已修订/已废止/即将失效
     */
    private String effectiveStatus;
    /**
     * 发布日期
     */
    private LocalDate publishDate;
    /**
     * 实施日期
     */
    private LocalDate effectiveDate;
    /**
     * 实施日期
     */
    private LocalDate implementDate;
    /**
     * 政策全文
     */
    private String content;
    /**
     * 原文链接
     */
    private String sourceUrl;
    /**
     * 来源网站
     */
    private String sourceName;
    /**
     * 附件URL数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;
    /**
     * 内容类型：法律法规/金融政策
     */
    private String contentType;
    /**
     * 内容摘要
     */
    private String summary;
    /**
     * 浏览量
     */
    private Integer viewCount;
    /**
     * 下载量
     */
    private Integer downloadCount;
    /**
     * 是否置顶
     */
    private Integer isTop;
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
