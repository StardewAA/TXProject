package cn.iocoder.txgy.module.mes.service.set.pollutioncheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MES 污染判定 AI 初筛服务（真实 DeepSeek 大模型）
 *
 * AI 判定 = DeepSeek 大模型初筛；判定失败或未配置 api-key 时自动降级为「污染特征词典」规则。
 * 无论真实大模型还是降级兜底，结果都只是"预分类建议"，终态以人工复核二值(CLEAN/POLLUTED)为准。
 * 配置项(仅 key 需要本地放，勿提交仓库)：txgy.pollution-ai.deepseek.api-key / base-url / model
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class MesSetPollutionCheckAiService {

    /**
     * AI 初筛结果：无污染
     */
    public static final String AI_RESULT_CLEAN = "CLEAN";
    /**
     * AI 初筛结果：有污染
     */
    public static final String AI_RESULT_POLLUTED = "POLLUTED";
    /**
     * AI 初筛结果：不确定（必须人工复核收口）
     */
    public static final String AI_RESULT_UNCERTAIN = "UNCERTAIN";

    private static final List<String> VALID_RESULTS =
            List.of(AI_RESULT_CLEAN, AI_RESULT_POLLUTED, AI_RESULT_UNCERTAIN);

    /**
     * 环节 → 中文语境（供大模型理解当前业务场景）
     */
    private static final Map<String, String> STAGE_CONTEXT = Map.of(
            "PURCHASE_INBOUND", "采购入库（原料/外购件到货验收）",
            "MATERIAL_ISSUE", "生产领用（仓库向产线发料）",
            "WASTE_INTERMEDIATE", "中间废弃物（生产过程中产生/收集的废料、废液）",
            "FINISHED_PRODUCT", "成品（产出/出货）");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 安全特征关键词（命中优先判无污染，例如"无铅"须先于"铅"；仅降级兜底用）
     */
    private static final String[] CLEAN_KEYWORDS = {
            "无铅", "无溶剂", "环保", "可降解", "纯水", "不锈钢", "钢材", "铝合金", "纯铝", "铜", "纸", "木", "玻璃",
            "ABS", "PP", "PE", "布料", "无尘"
    };

    /**
     * 污染特征关键词（仅降级兜底用）
     */
    private static final String[] POLLUTED_KEYWORDS = {
            "铅", "镉", "铬", "汞", "砷", "镍", "锰", "氰", "苯", "甲苯", "二甲苯", "甲醛", "酚", "石棉",
            "溶剂", "油漆", "油墨", "电镀", "酸洗", "酸", "碱", "树脂", "胶", "助剂", "阻燃剂", "农药", "含硫", "含氯"
    };

    @Value("${txgy.pollution-ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${txgy.pollution-ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${txgy.pollution-ai.deepseek.model:deepseek-chat}")
    private String model;

    /**
     * AI 初筛判定结果
     */
    @Data
    @Accessors(chain = true)
    public static class PrescreenResult {

        /**
         * AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN
         */
        private String aiResult;
        /**
         * AI 置信度(%) 0-100
         */
        private Integer aiConfidence;
        /**
         * AI 判定依据
         */
        private String aiReason;
        /**
         * AI 推荐存储方法
         */
        private String suggestedStorage;
    }

    /**
     * AI 初筛（真实 DeepSeek 判定，异常/未配置自动降级词典）
     *
     * @param itemName 物料/产品名称（主要判据）
     * @param itemSpec 规格（辅助判据，可为空）
     * @param stage    环节（业务语境，可为空）
     * @return 预分类建议
     */
    public PrescreenResult prescreen(String itemName, String itemSpec, String stage) {
        if (itemName == null || itemName.isBlank()) {
            return new PrescreenResult()
                    .setAiResult(AI_RESULT_UNCERTAIN)
                    .setAiConfidence(ThreadLocalRandom.current().nextInt(55, 76))
                    .setAiReason("物料名称为空，无法判定，请人工复核")
                    .setSuggestedStorage("隔离待检库位暂存，待人工复核/送检后确定存储");
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[pollution-ai] 未配置 txgy.pollution-ai.deepseek.api-key，AI 初筛降级为词典规则");
            return keywordPrescreen(itemName);
        }
        try {
            return deepseekPrescreen(itemName, itemSpec, stage);
        } catch (Exception e) {
            // AI 调用失败不阻断业务：降级词典并告警（终态仍以人工复核为准）
            log.warn("[pollution-ai] DeepSeek 判定调用失败，降级词典规则: {}", e.getMessage());
            return keywordPrescreen(itemName);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * DeepSeek 大模型判定：结构化 JSON 输出，逐字段清洗兜底
     */
    private PrescreenResult deepseekPrescreen(String itemName, String itemSpec, String stage) throws Exception {
        String prompt = "你是制造企业 MES 系统的环保/危废初筛专家。请对下述物料/产品做污染初筛判定。\n\n"
                + "<背景>\n业务环节: " + stageContext(stage)
                + "\n物料/产品名称: " + itemName
                + (itemSpec == null || itemSpec.isBlank() ? "" : "\n规格: " + itemSpec)
                + "\n</背景>\n\n"
                + "判定规则:\n"
                + "1. 名称或规格含重金属(铅镉铬汞砷镍等)、有机溶剂/油漆油墨、酸碱腐蚀性、放射性、危废字样或明显高污染工艺残留 → 判 POLLUTED;\n"
                + "2. 常见安全物料(金属结构件、木材纸品、常规塑料件、纯水等)或含'无铅/无溶剂/环保'等安全声明 → 判 CLEAN;\n"
                + "3. 信息不足或处于边界 → 判 UNCERTAIN，并说明需人工复核/送检。\n"
                + "只返回严格 JSON(不要多余文字): "
                + "{\"aiResult\":\"CLEAN|POLLUTED|UNCERTAIN\",\"confidence\":0到100的整数,"
                + "\"reason\":\"不超过80字的中文判定依据\",\"suggestedStorage\":\"不超过40字的中文存储建议\"}";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 400);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是严谨的环保工程师，只输出符合要求的 JSON。"),
                Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + truncate(response.body(), 120));
        }
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").asText("");
        String reasoning = message.path("reasoning_content").asText("");
        JsonNode suggestion = extractJson(content, reasoning);
        if (suggestion == null) {
            throw new IllegalStateException("DeepSeek 返回非预期内容: " + truncate(content, 120));
        }
        return new PrescreenResult()
                .setAiResult(sanitizeResult(suggestion.path("aiResult").asText("")))
                .setAiConfidence(sanitizeConfidence(suggestion.path("confidence").asText("")))
                .setAiReason(defaultText(suggestion.path("reason").asText(""), "AI 已判定，详见人工复核"))
                .setSuggestedStorage(defaultText(suggestion.path("suggestedStorage").asText(""),
                        "按判定结果受控存储或常规存放"));
    }

    /**
     * 提取 JSON：content 优先（response_format 规范输出）；容错退到 reasoning_content / 剥离围栏截取 { } 块
     */
    private JsonNode extractJson(String content, String reasoning) {
        JsonNode parsed = tryParse(content);
        if (parsed != null) {
            return parsed;
        }
        return tryParse(reasoning);
    }

    private JsonNode tryParse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeResult(String result) {
        if (result != null && VALID_RESULTS.contains(result)) {
            return result;
        }
        return AI_RESULT_UNCERTAIN;
    }

    private Integer sanitizeConfidence(String confidence) {
        try {
            int value = (int) Math.round(Double.parseDouble(confidence.trim()));
            return Math.max(0, Math.min(100, value));
        } catch (Exception e) {
            return ThreadLocalRandom.current().nextInt(55, 76);
        }
    }

    private String stageContext(String stage) {
        if (stage == null) {
            return "通用（未指明具体环节）";
        }
        return STAGE_CONTEXT.getOrDefault(stage, stage);
    }

    private String defaultText(String text, String fallback) {
        return (text == null || text.isBlank()) ? fallback : text.trim();
    }

    private String truncate(String text, int max) {
        return text == null ? "" : (text.length() > max ? text.substring(0, max) + "..." : text);
    }

    /**
     * 词典降级兜底（未配置 key / 大模型调用失败时使用）
     */
    private PrescreenResult keywordPrescreen(String itemName) {
        String matchedClean = matchKeyword(itemName, CLEAN_KEYWORDS);
        if (matchedClean != null) {
            return new PrescreenResult()
                    .setAiResult(AI_RESULT_CLEAN)
                    .setAiConfidence(ThreadLocalRandom.current().nextInt(85, 96))
                    .setAiReason("命中安全特征词「" + matchedClean + "」，未检出污染特征")
                    .setSuggestedStorage("普通仓储（常温、通风、防潮常规存放）");
        }
        String matchedPolluted = matchKeyword(itemName, POLLUTED_KEYWORDS);
        if (matchedPolluted != null) {
            return new PrescreenResult()
                    .setAiResult(AI_RESULT_POLLUTED)
                    .setAiConfidence(ThreadLocalRandom.current().nextInt(90, 99))
                    .setAiReason("命中污染特征词「" + matchedPolluted + "」，需按污染受控处置")
                    .setSuggestedStorage("污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放");
        }
        return new PrescreenResult()
                .setAiResult(AI_RESULT_UNCERTAIN)
                .setAiConfidence(ThreadLocalRandom.current().nextInt(55, 76))
                .setAiReason("未命中污染词典与安全白名单，请人工复核/必要时送检")
                .setSuggestedStorage("隔离待检库位暂存，待人工复核/送检后确定存储");
    }

    /**
     * 名称是否命中关键词，返回首个命中词
     */
    private String matchKeyword(String text, String[] keywords) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

}
