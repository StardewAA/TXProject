package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

/** 密钥自检出参（管理端"测试连接"按钮）。 */
@Data
public class BidReviewAiProbeRespVO {

    private Boolean ok;
    private String model;
    private Long costMs;
    private String errorMsg;
}
