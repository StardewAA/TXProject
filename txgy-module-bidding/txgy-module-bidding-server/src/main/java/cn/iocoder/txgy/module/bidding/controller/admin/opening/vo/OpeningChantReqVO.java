package cn.iocoder.txgy.module.bidding.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "开标房间 - 唱标 Request（宣读全部投标人报价，OPENING 阶段）")
@Data
public class OpeningChantReqVO {

    @Schema(description = "开标记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "开标记录ID不能为空")
    private Long openingId;

    @Schema(description = "唱标清单 JSON：[{bidderName,bidSectionCode,price,decryptStatus}]", requiredMode = Schema.RequiredMode.REQUIRED, example = "[{\"bidderName\":\"A公司\",\"price\":\"12800000\"}]")
    @NotBlank(message = "唱标清单不能为空")
    private String openList;

    @Schema(description = "投标人数量", example = "3")
    private Integer bidderCount;
}
