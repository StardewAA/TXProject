package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 规则集出参（含规则项明细）。 */
@Data
public class BidReviewRuleSetRespVO {

    private Long id;
    private String ruleSetCode;
    private String ruleSetName;
    private String source;
    private Long tenderFileId;
    private String parseStatus;
    private Integer ruleCnt;
    private Boolean enable;
    private Boolean defaultFlag;
    private String remark;
    private LocalDateTime createTime;
    /** 最近更新时间（管理端规则集列表展示"规则什么时候被改过"） */
    private LocalDateTime updateTime;
    /** 规则项明细（详情接口返回） */
    private List<BidReviewRuleItemRespVO> items;
}
