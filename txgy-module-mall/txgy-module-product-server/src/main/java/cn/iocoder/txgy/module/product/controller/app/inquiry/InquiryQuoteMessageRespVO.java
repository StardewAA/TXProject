package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InquiryQuoteMessageRespVO {
    private Long id;
    private Long quoteId;
    private String senderRole;
    private String clientMessageId;
    private String content;
    private LocalDateTime createTime;
}
