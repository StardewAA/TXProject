package cn.iocoder.txgy.module.product.controller.app.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InquiryQuoteMessageSendReqVO {
    @NotNull(message = "报价不能为空")
    private Long quoteId;

    @NotBlank(message = "消息幂等键不能为空")
    @Size(min = 8, max = 64, message = "消息幂等键长度须为 8 至 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "消息幂等键仅支持字母、数字、下划线和短横线")
    private String clientMessageId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
    private String content;
}
