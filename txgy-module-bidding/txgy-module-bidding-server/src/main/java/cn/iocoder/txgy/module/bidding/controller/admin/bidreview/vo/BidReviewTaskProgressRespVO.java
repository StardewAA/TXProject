package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

/** 任务进度出参（前端 1.5s 轮询用，字段越少越省流量）。 */
@Data
public class BidReviewTaskProgressRespVO {

    private Long id;
    private String status;
    private String currentStep;
    private Integer progress;
    private String verdict;
    private Integer issueCnt;
    private Integer highIssueCnt;
    private String errorCode;
    private String errorMsg;
    /** 是否废标（命中高危项即 true；口径见方案 §7「废标判定」） */
    private Boolean rejected;
    /** 废标原因（命中高危的规则名称清单） */
    private java.util.List<String> rejectReasons;
}
