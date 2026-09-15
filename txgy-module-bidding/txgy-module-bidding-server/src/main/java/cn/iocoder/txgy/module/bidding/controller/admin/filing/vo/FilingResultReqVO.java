package cn.iocoder.txgy.module.bidding.controller.admin.filing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 备案结果回推入参（05 §3.2 BID-SUP-FILE-01：监管 → 企业后台）。
 */
@Schema(description = "备案结果回推入参（BID-SUP-FILE-01）")
@Data
public class FilingResultReqVO {

    @Schema(description = "备案受理号")
    @NotBlank(message = "备案受理号不能为空")
    private String receiptNo;

    @Schema(description = "回推结果：ACCEPTED / REJECTED")
    @NotBlank(message = "回推结果不能为空")
    private String resultStatus;

    @Schema(description = "监管意见（驳回时必填）")
    private String opinion;

}
