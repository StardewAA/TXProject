package cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource;

import cn.iocoder.txgy.module.cms.dal.dataobject.CmsBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 工艺工法与案例库 DO
 *
 * @author yuting_feng
 */
@TableName(value = "cms_process_resource", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsProcessResourceDO extends CmsBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long resourceId;
    /**
     * 资源名称
     */
    private String title;
    /**
     * 类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案
     */
    private Integer resourceType;
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
     * 难度：1入门 2初级 3中级 4高级 5专家
     */
    private Integer difficulty;
    /**
     * 工序步骤JSON数组
     */
    private String processSteps;
    /**
     * 正文富文本HTML
     */
    private String content;
    /**
     * 配套视频ID
     */
    private Long videoId;
    /**
     * 附件ID数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentIds;
    /**
     * 关联标准ID数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> relatedStandardIds;
    /**
     * 关联材料ID数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> relatedMaterialIds;
    /**
     * 来源
     */
    private String source;
    /**
     * 作者
     */
    private String author;
    /**
     * 是否免费：0付费 1免费
     */
    private Integer isFree;
    /**
     * 浏览量
     */
    private Integer viewCount;
    /**
     * 点赞量
     */
    private Integer likeCount;
    /**
     * 收藏量
     */
    private Integer collectCount;
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
