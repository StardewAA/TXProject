package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.framework.ai.config.BidReviewAiProperties;
import cn.iocoder.txgy.module.bidding.framework.ai.core.BidReviewAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智慧标书审查 · AI 审查实现（直连 DeepSeek，零新增依赖）。
 *
 * <p>方案依据：§6.1~§6.4。三条硬约束的落地点：
 * ① 系统提示词固定包含 "json" 字样（DeepSeek JSON 模式前置要求）；
 * ② 只取 content，忽略 reasoning_content（reasoner 模型）；
 * ③ 不做图片/多模态——扫描件在 PARSE 步已失败，这里只会收到文本。
 *
 * <p>防幻觉闸门：阶段 B 的返回按 <b>规则白名单</b>过滤——模型编造的 ruleCode 直接丢弃并记日志。
 */
@Slf4j
@Service
public class BidReviewAiReviewServiceImpl implements BidReviewAiReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单批比对条数（太多会让模型"漏判"，太少浪费调用轮次） */
    private static final int BATCH_SIZE = 10;

    /** 阶段 A：招标文件正文截断（超出部分靠长文模型能力，P0 先截断，P1 分块） */
    private static final int TENDER_TEXT_LIMIT_FACTOR = 1;

    @Resource
    private BidReviewAiProperties properties;

    @Resource
    private BidReviewAiClient aiClient;

    @Override
    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    @Override
    public List<RuleDraft> extractRules(String tenderText, Long taskId) {
        int limit = properties.getChunkSize() == null ? 30_000 : properties.getChunkSize();
        String text = StrUtil.sub(StrUtil.nullToEmpty(tenderText), 0, limit * TENDER_TEXT_LIMIT_FACTOR);
        String json = aiClient.chatJson(taskId, "RULE", SYSTEM_PROMPT_RULE, buildRulePrompt(text));
        List<RuleDraft> drafts = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode rules = root.path("rules");
            if (!rules.isArray()) {
                return drafts;
            }
            int index = 1;
            for (JsonNode node : rules) {
                String ruleName = node.path("ruleName").asText("");
                if (StrUtil.isBlank(ruleName)) {
                    continue;
                }
                String code = StrUtil.blankToDefault(node.path("ruleCode").asText(""), String.format("T%03d", index));
                drafts.add(new RuleDraft(
                        StrUtil.sub(code, 0, 32),
                        StrUtil.sub(ruleName, 0, 120),
                        normalize(node.path("category").asText(""), RULE_CATEGORIES, "FORM"),
                        normalize(node.path("ruleType").asText(""), RULE_TYPES, "FORMAT"),
                        normalize(node.path("checkMode").asText(""), CHECK_MODES, "AI"),
                        normalize(node.path("severity").asText(""), SEVERITIES, "MEDIUM"),
                        toDecimal(node.path("scoreMax")),
                        StrUtil.sub(node.path("requirement").asText(""), 0, 2000),
                        StrUtil.sub(node.path("evidenceHint").asText(""), 0, 500),
                        StrUtil.sub(node.path("sourceRef").asText(""), 0, 200)));
                index++;
            }
        } catch (Exception e) {
            log.warn("[extractRules][规则抽取结果解析失败 taskId={}]", taskId, e);
        }
        return drafts;
    }

    @Override
    public List<ItemResult> reviewRules(List<BidReviewRuleItemDO> rules, String bidText, Long taskId) {
        List<ItemResult> results = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            return results;
        }
        int limit = properties.getChunkSize() == null ? 30_000 : properties.getChunkSize();
        String text = StrUtil.sub(StrUtil.nullToEmpty(bidText), 0, limit);
        Map<String, String> whitelist = new HashMap<>();
        for (BidReviewRuleItemDO rule : rules) {
            whitelist.put(rule.getRuleCode(), rule.getSeverity());
        }
        // 分批判比（每批 10 条），单批失败不影响其它批次
        for (int i = 0; i < rules.size(); i += BATCH_SIZE) {
            List<BidReviewRuleItemDO> batch = rules.subList(i, Math.min(rules.size(), i + BATCH_SIZE));
            try {
                String json = aiClient.chatJson(taskId, "GENERATE", SYSTEM_PROMPT_REVIEW, buildReviewPrompt(batch, text));
                JsonNode root = MAPPER.readTree(json);
                JsonNode array = root.path("results");
                if (!array.isArray()) {
                    continue;
                }
                for (JsonNode node : array) {
                    String ruleCode = node.path("ruleCode").asText("");
                    // 【防幻觉】白名单过滤：不在本批次规则内的 ruleCode 一律丢弃
                    if (StrUtil.isBlank(ruleCode) || !whitelist.containsKey(ruleCode)) {
                        log.warn("[reviewRules][丢弃越界 ruleCode={} taskId={}]", ruleCode, taskId);
                        continue;
                    }
                    results.add(new ItemResult(
                            ruleCode,
                            normalize(node.path("result").asText(""), REVIEW_RESULTS, "NOT_FOUND"),
                            StrUtil.sub(node.path("reason").asText(""), 0, 2000),
                            StrUtil.sub(node.path("hitLocation").asText(""), 0, 120),
                            StrUtil.sub(node.path("evidenceText").asText(""), 0, 1000),
                            StrUtil.sub(node.path("suggestion").asText(""), 0, 2000),
                            toDecimal(node.path("confidence")),
                            toDecimal(node.path("score"))));
                }
            } catch (Exception e) {
                // 单批失败：其余批次继续；该批规则最终按"未判定"处理（不误判为通过）
                log.warn("[reviewRules][批次比对失败 taskId={} batchStart={}]", taskId, i, e);
            }
        }
        return results;
    }

    private static final List<String> RULE_CATEGORIES = List.of("FORM", "QUALIFICATION", "RESPONSE", "BUSINESS", "TECH", "PRICE");
    private static final List<String> RULE_TYPES = List.of("DISQUALIFY", "QUALIFICATION", "FORMAT", "SCORING");
    private static final List<String> CHECK_MODES = List.of("RULE", "AI");
    private static final List<String> SEVERITIES = List.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> REVIEW_RESULTS = List.of("PASS", "RISK", "FAIL", "NOT_FOUND");

    private static final String SYSTEM_PROMPT_RULE = """
            你是招投标评审专家，负责从招标文件中抽取"评审要素"。
            只输出 json（json 对象，不要 markdown 代码块，不要解释文字），结构如下：
            {"rules":[{"ruleCode":"R001","ruleName":"投标函是否盖章签字","category":"FORM","ruleType":"DISQUALIFY",
            "checkMode":"AI","severity":"HIGH","scoreMax":null,"requirement":"招标文件原文要求",
            "evidenceHint":"检查点提示","sourceRef":"第三章 评审办法"}]}
            约束：
            1) category 只能取 FORM/QUALIFICATION/RESPONSE/BUSINESS/TECH/PRICE；
               ruleType 只能取 DISQUALIFY/QUALIFICATION/FORMAT/SCORING；
               checkMode 只能取 RULE(关键词可判定)/AI(需语义判断)；severity 只能取 HIGH/MEDIUM/LOW；
            2) 只抽取能落到"标书文本"上核对的要求，不要抽取流程性或时间性要求；
            3) requirement 必须是招标文件里的原文或紧贴原文的摘录，不要自己编；
            4) 最多输出 40 条，按重要性排序。
            """;

    private static final String SYSTEM_PROMPT_REVIEW = """
            你是招投标评审专家，负责逐条核对投标文件（标书）是否满足招标要求。
            只输出 json（json 对象，不要 markdown 代码块，不要解释文字），结构如下：
            {"results":[{"ruleCode":"R001","result":"PASS","reason":"判定理由","hitLocation":"第 12 页 / 第三章",
            "evidenceText":"标书原文片段（≤200 字）","suggestion":"整改建议（result 非 PASS 时必填）",
            "confidence":0.8,"score":8}]}
            约束：
            1) result 只能取 PASS(满足) / RISK(部分满足或存疑) / FAIL(明确不满足) / NOT_FOUND(标书中找不到相关内容)；
            2) **必须给出 evidenceText 原文片段与 hitLocation 定位**；标书里找不到对应内容就返回 NOT_FOUND、
               evidenceText 留空（系统会统一写"标书没有对应内容"），严禁编造原文；证据不足时 confidence 不得高于 0.5；
            3) ruleCode 必须来自输入 rules 列表，不得新增；
            4) 评分项（ruleType=SCORING）请在 score 里给出你判断的得分，其它类型 score 留 null。
            """;

    private String buildRulePrompt(String tenderText) {
        return "【招标文件正文】\n" + tenderText + "\n\n请按上述 json 结构输出评审要素。";
    }

    private String buildReviewPrompt(List<BidReviewRuleItemDO> batch, String bidText) {
        StringBuilder rules = new StringBuilder();
        for (BidReviewRuleItemDO rule : batch) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", rule.getRuleCode());
            item.put("ruleName", rule.getRuleName());
            item.put("category", rule.getCategory());
            item.put("ruleType", rule.getRuleType());
            item.put("severity", rule.getSeverity());
            item.put("scoreMax", rule.getScoreMax());
            item.put("requirement", StrUtil.sub(StrUtil.nullToEmpty(rule.getRequirement()), 0, 500));
            item.put("evidenceHint", StrUtil.nullToEmpty(rule.getEvidenceHint()));
            rules.append(toJson(item)).append('\n');
        }
        return "【待核对规则】\n" + rules + "\n【投标文件正文】\n" + bidText + "\n\n请按上述 json 结构输出每条规则的核对结果。";
    }

    private String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalize(String value, List<String> allowed, String fallback) {
        String v = StrUtil.blankToDefault(value, "").trim().toUpperCase();
        return allowed.contains(v) ? v : fallback;
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
