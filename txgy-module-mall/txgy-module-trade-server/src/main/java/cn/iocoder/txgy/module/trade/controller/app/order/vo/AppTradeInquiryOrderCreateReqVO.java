package cn.iocoder.txgy.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 已采纳询价报价创建订单 Request VO")
@Data
public class AppTradeInquiryOrderCreateReqVO {

    @Schema(description = "已采纳报价编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "报价编号不能为空")
    @Positive(message = "报价编号必须大于 0")
    private Long quoteId;

    @Schema(description = "收货地址编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "收货地址不能为空")
    @Positive(message = "收货地址编号必须大于 0")
    private Long addressId;

    @Schema(description = "采购备注", example = "按报价约定发货")
    @Size(max = 200, message = "采购备注不能超过 200 个字符")
    private String remark;

    @Schema(description = "客户端请求幂等键", requiredMode = Schema.RequiredMode.REQUIRED, example = "rfq-order-20260921-001")
    @NotBlank(message = "请求幂等键不能为空")
    @Size(max = 64, message = "请求幂等键不能超过 64 个字符")
    private String requestKey;

}
