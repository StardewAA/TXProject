package cn.iocoder.txgy.module.cms.dal.dataobject.Standard;

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
 * 行业标准规范库 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_standard", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsStandardDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long standardId;
    /**
     * 标准编号，如GB 50264-2013
     */
    private String standardNo;
    /**
     * 标准名称
     */
    private String standardName;
    /**
     * 类型：1国标 2行标 3地方标 4团标 5企业标
     */
    private Integer standardType;
    /**
     * 行标行业代号
     */
    private String industryCode;
    /**
     * 发布机构
     */
    private String publishOrg;
    /**
     * 发布日期
     */
    private LocalDate publishDate;
    /**
     * 实施日期
     */
    private LocalDate implementDate;
    /**
     * 状态：1现行 2即将实施 3废止 4被替代
     */
    private Integer status;
    /**
     * 废止日期
     */
    private LocalDate abolishDate;
    /**
     * 替代标准ID
     */
    private Long replaceStandardId;
    /**
     * 适用领域JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> industryField;
    /**
     * 防护对象JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> protectObject;
    /**
     * 施工场景JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scene;
    /**
     * 标准摘要
     */
    private String summary;
    /**
     * 关键词
     */
    private String keywords;
    /**
     * 全文附件ID
     */
    private Long fileId;
    /**
     * 封面图URL
     */
    private String coverImage;
    /**
     * 浏览量
     */
    private Integer viewCount;
    /**
     * 下载量
     */
    private Integer downloadCount;
    /**
     * 订阅数
     */
    private Integer subscribeCount;
    /**
     * 是否置顶
     */
    private Integer isTop;
    /**
     * 审核状态
     */
    private Integer auditStatus;
    /**
     * 审核备注
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
