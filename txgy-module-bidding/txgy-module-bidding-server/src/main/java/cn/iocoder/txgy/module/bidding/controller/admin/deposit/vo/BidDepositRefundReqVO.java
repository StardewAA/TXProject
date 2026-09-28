package cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 投标保证金退还 Request（招标方合同签订后退还时调用）")
@Data
public class BidDepositRefundReqVO {

    @Schema(description = "保证金记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "7101")
    @NotNull(message = "保证金记录ID不能为空")
    private Long depositId;

    @Schema(description = "退还状态（字典；缺省 REFUNDED）")
    private String refundStatus;

    @Schema(description = "备注")
    private String remark;
}
