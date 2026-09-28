package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 证照批量验真入参（五页改造缺接口契约 Q2，2026-09-28：certIds[]+verifyChannel，逐条结果）。
 */
@Schema(description = "管理后台 - 证照批量验真入参")
@Data
public class CertVerifyBatchReqVO {

    @Schema(description = "证照 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "证照不能为空")
    private List<Long> certIds;

    @Schema(description = "验真渠道（缺省 FOUR_PLATFORM；同单条验真）", example = "FOUR_PLATFORM")
    private String verifyChannel;
}
