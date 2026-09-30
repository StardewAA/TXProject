package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 智慧标书审查 · AI 审查能力（提示词构造 + 结果解析 + 白名单过滤）。
 *
 * <p>方案依据：§6.1（提示词三层）、§6.2（两阶段）、§6.3（输出校验与兜底）。
 */
public interface BidReviewAiReviewService {

    /** 是否具备 AI 能力（未配置时流水线走降级：仅规则匹配）。 */
    boolean isAvailable();

    /**
     * 阶段 A：从招标文件正文抽取规则集（废标项/资格要求/格式要求/评分项）。
     *
     * @param tenderText 招标文件正文
     * @return 规则草稿（调用方落库为规则集 + 规则项，可人工校正）
     */
    List<RuleDraft> extractRules(String tenderText, Long taskId);

    /**
     * 阶段 B：规则 × 标书正文逐条比对。
     *
     * @param rules   参与比对的规则（启用中）
     * @param bidText 标书正文（按 chunkSize 截断/分块）
     * @return 每条规则的判定结果（ruleCode 必在 rules 白名单内，否则丢弃）
     */
    List<ItemResult> reviewRules(List<BidReviewRuleItemDO> rules, String bidText, Long taskId);

    /** 单条规则判定结果 */
    record ItemResult(String ruleCode, String result, String reason, String hitLocation,
                      String evidenceText, String suggestion, BigDecimal confidence, BigDecimal score) {

        /** PASS/RISK/FAIL/NOT_FOUND */
        public boolean isProblem() {
            return "RISK".equals(result) || "FAIL".equals(result) || "NOT_FOUND".equals(result);
        }

        public boolean evidenceLack() {
            return evidenceText == null || evidenceText.isBlank();
        }
    }

    /** 规则草稿（阶段 A 产出） */
    record RuleDraft(String ruleCode, String ruleName, String category, String ruleType, String checkMode,
                     String severity, BigDecimal scoreMax, String requirement, String evidenceHint,
                     String sourceRef) {
    }
}
