package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 规则项出参。 */
@Data
public class BidReviewRuleItemRespVO {

    private Long id;
    private Long ruleSetId;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String ruleType;
    private String checkMode;
    private String severity;
    private BigDecimal scoreMax;
    private String requirement;
    private String evidenceHint;
    private String sourceRef;
    private Integer sort;
    private Boolean enable;
}
