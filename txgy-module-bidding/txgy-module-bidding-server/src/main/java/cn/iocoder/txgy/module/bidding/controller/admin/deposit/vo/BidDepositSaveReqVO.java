package cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 投标保证金登记 Request（投标方缴纳时调用，登记时校验 2% 限额）")
@Data
public class BidDepositSaveReqVO {

    @Schema(description = "标段编号（BID_SECTION_CODE）", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001001001")
    @NotNull(message = "标段编号不能为空")
    private String bidSectionCode;

    @Schema(description = "投标主体ID（bidding_subject.id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "投标主体不能为空")
    private Long subjectId;

    @Schema(description = "保证金金额（元）", requiredMode = Schema.RequiredMode.REQUIRED, example = "100000.00")
    @NotNull(message = "保证金金额不能为空")
    private BigDecimal amount;

    @Schema(description = "币种（CURRENCY_CODE）", example = "CNY")
    private String currencyCode;

    @Schema(description = "缴纳方式（MARGIN_PAY_TYPE 字典）")
    private String payType;

    @Schema(description = "缴纳状态（DEPOSIT_STATUS 字典）")
    private String status;

    @Schema(description = "缴纳凭证文件ID（FileApi）")
    private Long fileId;

    @Schema(description = "备注")
    private String remark;
}
