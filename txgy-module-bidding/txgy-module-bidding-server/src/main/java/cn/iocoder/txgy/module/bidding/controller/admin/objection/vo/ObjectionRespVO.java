package cn.iocoder.txgy.module.bidding.controller.admin.objection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 异议 Response")
@Data
public class ObjectionRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "异议类型")
    private String objectionType;

    @Schema(description = "提出主体ID")
    private Long subjectId;

    @Schema(description = "异议内容")
    private String content;

    @Schema(description = "提出时间")
    private LocalDateTime proposeTime;

    @Schema(description = "状态（PENDING 待答复 / REPLIED 已答复）")
    private String status;

    @Schema(description = "答复时间")
    private LocalDateTime replyTime;

    @Schema(description = "答复内容")
    private String replyContent;

    @Schema(description = "答复是否超期（条例第22条：收到后3日内答复）")
    private Boolean replyOverdue;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
