package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 问题项分页入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewIssuePageReqVO extends PageParam {

    private Long taskId;
    private String severity;
    private String category;
    /** OPEN/CONFIRMED/IGNORED/FIXED */
    private String status;
    /** AI/RULE/MANUAL */
    private String source;
    /**
     * 是否"证据不足"（true=只看 AI 给不出标书原文证据的问题项）。
     *
     * <p>口径：证据不足仍按规则原级别计（高危即高危），但复核人必须能单独筛出来看，
     * 避免"没有原文依据的判断"被混在大量有据问题里。
     */
    private Boolean evidenceLack;
}
