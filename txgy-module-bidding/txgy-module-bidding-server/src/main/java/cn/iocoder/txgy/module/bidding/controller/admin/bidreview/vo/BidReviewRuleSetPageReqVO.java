package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 规则集分页入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewRuleSetPageReqVO extends PageParam {

    private String ruleSetName;
    /** PLATFORM / TENDER_DOC / MIXED */
    private String source;
    private Boolean enable;
}
