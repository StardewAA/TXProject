package cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 记录招标文件下载 Request（投标方下载时调用，落下载名单）")
@Data
public class TenderDocDownloadRecordReqVO {

    @Schema(description = "招标项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001001")
    @NotNull(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（空=项目级）", example = "I6101002026000001001001")
    private String bidSectionCode;

    @Schema(description = "关联附件行ID（bidding_attachment.id，招标文件）")
    private Long attachmentId;

    @Schema(description = "FileApi 文件对象主键（一致性依据）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @Schema(description = "投标主体ID（bidding_subject.id）")
    private Long subjectId;

    @Schema(description = "备注")
    private String remark;
}
