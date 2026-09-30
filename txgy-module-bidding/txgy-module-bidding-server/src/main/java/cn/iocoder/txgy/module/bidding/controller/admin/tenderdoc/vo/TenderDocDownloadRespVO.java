package cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 招标文件下载记录（下载名单条目）")
@Data
public class TenderDocDownloadRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "关联附件行ID")
    private Long attachmentId;

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "投标主体ID")
    private Long subjectId;

    @Schema(description = "下载用户ID")
    private Long userId;

    @Schema(description = "下载时间")
    private LocalDateTime downloadTime;

    @Schema(description = "下载来源IP")
    private String ip;

    @Schema(description = "备注")
    private String remark;
}
