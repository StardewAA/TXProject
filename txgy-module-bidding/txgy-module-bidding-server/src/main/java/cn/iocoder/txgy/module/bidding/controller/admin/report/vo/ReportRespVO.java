package cn.iocoder.txgy.module.bidding.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 定标书面报告 Response")
@Data
public class ReportRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "报告编号")
    private String reportCode;

    @Schema(description = "报告名称")
    private String reportName;

    @Schema(description = "报告文件 FileApi 主键")
    private Long fileId;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "定标时间（中标结果公告发布时间，招标投标法第47条 15 日时限起算点）")
    private LocalDateTime awardTime;

    @Schema(description = "状态（SUBMITTED 已提交）")
    private String status;

    @Schema(description = "是否超期未提交（招标投标法第47条：定标后15日内提交）")
    private Boolean reportOverdue;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
