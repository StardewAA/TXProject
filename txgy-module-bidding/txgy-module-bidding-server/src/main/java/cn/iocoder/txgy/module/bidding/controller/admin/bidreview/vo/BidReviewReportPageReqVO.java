package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审查报告台账分页入参（「报告与统计」页下方的报告台账）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewReportPageReqVO extends PageParam {

    /** 报告编号（模糊） */
    private String reportNo;
    /** 结论：PASS/RISK/FAIL */
    private String verdict;
}
