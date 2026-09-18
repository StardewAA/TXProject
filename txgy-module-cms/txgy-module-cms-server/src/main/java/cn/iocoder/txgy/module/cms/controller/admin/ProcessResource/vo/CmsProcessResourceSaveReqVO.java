package cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 工艺工法与案例库创建/修改 Request VO")
@Data
public class CmsProcessResourceSaveReqVO {

    @Schema(description = "主键（更新时必传）")
    private Long resourceId;
    @NotNull(message = "资源名称不能为空")
    @Schema(description = "资源名称")
    private String title;
    @NotNull(message = "类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案不能为空")
    @Schema(description = "类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案")
    private Integer resourceType;
    @Schema(description = "适用领域JSON数组")
    private List<String> industryField;
    @Schema(description = "防护对象JSON数组")
    private List<String> protectObject;
    @Schema(description = "施工场景JSON数组")
    private List<String> scene;
    @NotNull(message = "难度：1入门 2初级 3中级 4高级 5专家不能为空")
    @Schema(description = "难度：1入门 2初级 3中级 4高级 5专家")
    private Integer difficulty;
    @Schema(description = "工序步骤JSON数组")
    private String processSteps;
    @NotNull(message = "正文富文本HTML不能为空")
    @Schema(description = "正文富文本HTML")
    private String content;
    @Schema(description = "配套视频ID")
    private Long videoId;
    @Schema(description = "附件ID数组")
    private List<String> attachmentIds;
    @Schema(description = "关联标准ID数组")
    private List<String> relatedStandardIds;
    @Schema(description = "关联材料ID数组")
    private List<String> relatedMaterialIds;
    @Schema(description = "来源")
    private String source;
    @Schema(description = "作者")
    private String author;
    @Schema(description = "是否免费：0付费 1免费")
    private Integer isFree;
    @Schema(description = "浏览量")
    private Integer viewCount;
    @Schema(description = "点赞量")
    private Integer likeCount;
    @Schema(description = "收藏量")
    private Integer collectCount;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "发布状态")
    private Integer publishStatus;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
}
