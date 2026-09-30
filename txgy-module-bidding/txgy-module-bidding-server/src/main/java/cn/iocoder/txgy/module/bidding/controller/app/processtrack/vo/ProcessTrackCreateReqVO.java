package cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 投标流程跟踪建档 Request（关注项目即建档，每用户每标段一条）")
@Data
public class ProcessTrackCreateReqVO {

    @Schema(description = "投标员工用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "标段编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001")
    @NotBlank(message = "标段编号不能为空")
    private String bidSectionCode;

    @Schema(description = "招标项目编号", example = "I6101002026000001001")
    private String tenderProjectCode;

    @Schema(description = "项目名称（快照）", example = "XX市政工程招标项目")
    private String projectName;

    @Schema(description = "发布单位（快照）", example = "XX市住房和城乡建设局")
    private String issuerName;
}
