package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 智慧标书审查规则项（表 bidding_bid_review_rule_item）。
 *
 * <p>方案依据：§2.2、§6.1（规则提示 = 把本表条目作为 JSON 交给模型）。
 * <p>设计遵循/针对：rule_code 是"AI 输出白名单"——模型返回的 ruleCode 不在本表内即丢弃，
 * 这是防幻觉的硬闸门；check_mode=RULE 的条目走确定性匹配，不消耗模型调用。
 */
@TableName("bidding_bid_review_rule_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewRuleItemDO extends TenantBaseDO {

    private Long id;
    /** 规则集 id */
    private Long ruleSetId;
    /** 规则编码（AI 输出白名单） */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 类别 code：FORM/QUALIFICATION/RESPONSE/BUSINESS/TECH/PRICE */
    private String category;
    /** 类型 code：DISQUALIFY/QUALIFICATION/FORMAT/SCORING */
    private String ruleType;
    /** 检查方式 code：RULE/AI */
    private String checkMode;
    /** 严重级别 code：HIGH/MEDIUM/LOW */
    private String severity;
    /** 满分（rule_type=SCORING 时有值） */
    private BigDecimal scoreMax;
    /** 要求原文 */
    private String requirement;
    /** 检查点提示 */
    private String evidenceHint;
    /** 出处（条款号/页码） */
    private String sourceRef;
    /** 排序 */
    private Integer sort;
    /** 是否启用 */
    private Boolean enable;
}
