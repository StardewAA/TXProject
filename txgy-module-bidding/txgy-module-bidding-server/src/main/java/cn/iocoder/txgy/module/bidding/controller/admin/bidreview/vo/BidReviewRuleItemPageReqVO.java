package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 规则项分页入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewRuleItemPageReqVO extends PageParam {

    private Long ruleSetId;
    private String ruleName;
    private String category;
    private String ruleType;
}
