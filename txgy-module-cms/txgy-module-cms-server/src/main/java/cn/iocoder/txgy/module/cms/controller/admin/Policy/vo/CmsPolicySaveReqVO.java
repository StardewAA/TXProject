package cn.iocoder.txgy.module.cms.controller.admin.Policy.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 政策资讯创建/修改 Request VO")
@Data
public class CmsPolicySaveReqVO {

    @Schema(description = "主键（更新时必传）")
    private Long policyId;
    @NotNull(message = "政策标题不能为空")
    @Schema(description = "政策标题")
    private String title;
    @Schema(description = "层级：1国家 2省 3市 4区县")
    private Integer policyLevel;
    @Schema(description = "发布部门")
    private String publishDept;
    @Schema(description = "主题分类")
    private String category;
    @Schema(description = "效力状态：现行有效/已修订/已废止/即将失效")
    private String effectiveStatus;
    @Schema(description = "发布日期")
    private LocalDate publishDate;
    @Schema(description = "实施日期")
    private LocalDate effectiveDate;
    @Schema(description = "实施日期")
    private LocalDate implementDate;
    @Schema(description = "政策全文")
    private String content;
    @Schema(description = "原文链接")
    private String sourceUrl;
    @Schema(description = "来源网站")
    private String sourceName;
    @Schema(description = "附件URL数组")
    private List<String> attachmentUrls;
    @Schema(description = "内容类型：法律法规/金融政策")
    private String contentType;
    @Schema(description = "内容摘要")
    private String summary;
    @Schema(description = "浏览量")
    private Integer viewCount;
    @Schema(description = "下载量")
    private Integer downloadCount;
    @Schema(description = "是否置顶")
    private Integer isTop;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "审核备注（驳回原因）")
    private String auditRemark;
    @Schema(description = "发布状态")
    private Integer publishStatus;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
}
