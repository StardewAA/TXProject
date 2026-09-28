package cn.iocoder.txgy.module.bidding.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 创建开标房间 Request（一条招标项目一条开标记录；平台方主持人）")
@Data
public class OpeningCreateReqVO {

    @Schema(description = "招标项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001001")
    @NotBlank(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（空=覆盖招标项目全部标段）", example = "I6101002026000001001001")
    private String bidSectionCode;

    @Schema(description = "开标时间（默认当前）")
    private LocalDateTime openingTime;

    @Schema(description = "开标主持人（平台方）", example = "平台小张")
    private String operatorName;
}
