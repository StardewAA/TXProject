package cn.iocoder.txgy.module.erp.service.recognition.provider;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ERP 视觉大模型识别适配器（通义千问 Qwen-VL）。
 *
 * <p>调用 DashScope OpenAI 兼容端点，传入图片 base64 与结构化抽取 prompt，
 * 直接得到符合 {@link ErpRecognitionDocument} 字段约定的 JSON。零样本即可处理任意版式订单，
 * 因为 Qwen-VL 已在数十亿图像（含海量中文单据/发票/订单）上预训练。</p>
 *
 * <p>作为新默认 Provider，Tesseract 仅作为 opt-in 备选（{@code erp.recognition.provider=tesseract}）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erp.recognition.provider", havingValue = "qwen-vision", matchIfMissing = true)
public class QwenVisionRecognitionProvider implements DocumentRecognitionProvider {

    private static final String DEFAULT_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen-vl-plus";
    private static final int DEFAULT_PDF_DPI = 150;
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_PAGES_PER_REQUEST = 3;
    private static final double DEFAULT_CONFIDENCE = 0.92;

    @Resource
    private RecognitionSourceFileSupport sourceFileSupport;

    @Value("${erp.recognition.qwen.api-key:${DASHSCOPE_API_KEY:}}")
    private String apiKey;

    @Value("${erp.recognition.qwen.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;

    @Value("${erp.recognition.qwen.model:" + DEFAULT_MODEL + "}")
    private String model;

    @Value("${erp.recognition.qwen.timeout-seconds:" + DEFAULT_TIMEOUT_SECONDS + "}")
    private long timeoutSeconds;

    @Value("${erp.recognition.qwen.enable-thinking:false}")
    private boolean enableThinking;

    @Value("${erp.recognition.qwen.pdf-dpi:" + DEFAULT_PDF_DPI + "}")
    private int pdfDpi;

    /**
     * 是否启用 few-shot：将 classpath:/recognition-examples/ 下的「样例订单图 + 标准 JSON」
     * 作为多轮对话前缀，稳定模型输出格式并强化字段变体识别（供方/客户/开单日期等）。
     */
    @Value("${erp.recognition.qwen.few-shot.enabled:true}")
    private boolean fewShotEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** few-shot 样例记录：样例业务类型（决定 user prompt）、图片 dataUrl、标准答案 JSON */
    private record Exemplar(String documentType, String imageDataUrl, String answerJson) {
    }

    /** 懒加载并缓存（图片 base64 只编码一次）；classpath 无法列目录，按 example-1..N 探测 */
    private volatile List<Exemplar> exemplarCache;

    private List<Exemplar> loadExemplars() {
        List<Exemplar> cached = exemplarCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (exemplarCache == null) {
                List<Exemplar> list = new ArrayList<>();
                for (int i = 1; i <= 8; i++) {
                    String base = "/recognition-examples/example-" + i;
                    try (InputStream imgIn = getClass().getResourceAsStream(base + ".png");
                         InputStream jsonIn = getClass().getResourceAsStream(base + ".json")) {
                        if (imgIn == null || jsonIn == null) {
                            break;
                        }
                        String answer = new String(jsonIn.readAllBytes(), StandardCharsets.UTF_8);
                        String dataUrl = "data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(imgIn.readAllBytes());
                        list.add(new Exemplar(extractDocumentType(answer), dataUrl, answer));
                    } catch (IOException e) {
                        log.warn("[loadExemplars][样例 {} 加载失败，跳过: {}]", i, e.getMessage());
                        break;
                    }
                }
                exemplarCache = List.copyOf(list);
                log.info("[loadExemplars][加载 {} 个 few-shot 样例]", exemplarCache.size());
            }
            return exemplarCache;
        }
    }

    /** 从标准答案 JSON 中读 documentType，决定该样例轮次使用的 prompt */
    private static String extractDocumentType(String answerJson) {
        try {
            Map<String, Object> map = JsonUtils.parseObject(answerJson, new TypeReference<>() {});
            Object type = map == null ? null : map.get("documentType");
            if (type != null && StringUtils.hasText(type.toString())) {
                return type.toString();
            }
        } catch (Exception ignored) {
            // 样例损坏时回退默认类型
        }
        return "PURCHASE_ORDER";
    }

    @Override
    public String getType() {
        return "QWEN_VISION";
    }

    @Override
    public String recognize(String sourceFileUrl, String bizType) {
        validateApiKey();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("txgy-erp-vision-");
            Path inputFile = sourceFileSupport.downloadSourceFile(sourceFileUrl, workDir);
            List<Path> images = sourceFileSupport.isPdf(inputFile)
                    ? sourceFileSupport.convertPdfToImages(inputFile, workDir, pdfDpi, timeoutSeconds)
                    : List.of(inputFile);
            if (images.isEmpty()) {
                throw new IllegalStateException("待识别文件未生成可用图片");
            }
            String prompt = buildPrompt(bizType);
            String rawContent = callVisionModelInBatches(images, prompt);
            if (!StringUtils.hasText(rawContent)) {
                throw new IllegalStateException("Qwen-VL 返回空内容，请检查图片是否清晰或重试");
            }
            return buildResponse(rawContent, bizType);
        } catch (IOException e) {
            throw new IllegalStateException("Qwen-VL 文件处理失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qwen-VL 调用被中断", e);
        } finally {
            sourceFileSupport.deleteDirectory(workDir);
        }
    }

    private void validateApiKey() {
        if (!StringUtils.hasText(apiKey) || "sk-xxxx".equals(apiKey)) {
            throw new IllegalStateException("DASHSCOPE_API_KEY 未配置，无法调用 Qwen-VL；"
                    + "请在 .env 中填入或切换 ERP_RECOGNITION_PROVIDER=tesseract 回退");
        }
    }

    /** 多页 PDF 分批调用：每 MAX_PAGES_PER_REQUEST 页一次，items 合并 */
    private String callVisionModelInBatches(List<Path> images, String prompt)
            throws IOException, InterruptedException {
        if (images.size() <= MAX_PAGES_PER_REQUEST) {
            return callVisionModel(images, prompt);
        }
        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < images.size(); i += MAX_PAGES_PER_REQUEST) {
            int end = Math.min(i + MAX_PAGES_PER_REQUEST, images.size());
            List<Path> batch = images.subList(i, end);
            String partial = callVisionModel(batch, prompt);
            if (merged.length() > 0) {
                merged.append("\n---PAGE_BREAK---\n");
            }
            merged.append(partial);
        }
        return merged.toString();
    }

    private String callVisionModel(List<Path> images, String prompt)
            throws IOException, InterruptedException {
        String requestBody = buildRequestBody(images, prompt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("DashScope 调用失败 HTTP " + response.statusCode()
                    + "：" + truncate(response.body(), 500));
        }
        return extractContent(response.body());
    }

    private String buildRequestBody(List<Path> images, String prompt) throws IOException {
        List<Object> messages = new ArrayList<>();
        // few-shot 多轮前缀：user(样例图+指令) → assistant(标准 JSON)，让模型学会输出格式
        if (fewShotEnabled) {
            for (Exemplar exemplar : loadExemplars()) {
                messages.add(Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", buildPrompt(exemplar.documentType())),
                        Map.of("type", "image_url", "image_url", Map.of("url", exemplar.imageDataUrl())))));
                messages.add(Map.of("role", "assistant", "content", exemplar.answerJson()));
            }
        }
        // 真实待识别请求
        List<Object> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));
        for (Path image : images) {
            byte[] bytes = Files.readAllBytes(image);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mediaType = guessMediaType(image);
            String dataUrl = "data:" + mediaType + ";base64," + base64;
            contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }
        messages.add(Map.of("role", "user", "content", contentParts));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (enableThinking) {
            body.put("enable_thinking", true);
        }
        return JsonUtils.toJsonString(body);
    }

    /** 从 DashScope OpenAI 兼容响应里取 choices[0].message.content */
    private String extractContent(String responseBody) {
        Map<String, Object> root = JsonUtils.parseObject(responseBody, new TypeReference<>() {});
        if (root == null) {
            throw new IllegalStateException("DashScope 响应非 JSON：" + truncate(responseBody, 200));
        }
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            Object error = root.get("error");
            throw new IllegalStateException("DashScope 返回无 choices："
                    + (error != null ? error : truncate(responseBody, 200)));
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("DashScope choices[0] 格式异常");
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) {
            throw new IllegalStateException("DashScope choices[0].message 格式异常");
        }
        Object content = msg.get("content");
        return content == null ? null : content.toString();
    }

    private String buildPrompt(String bizType) {
        String bizDesc = switch (bizType) {
            case "PURCHASE_ORDER" -> "采购订单";
            case "PURCHASE_RETURN" -> "采购退货单";
            case "SUPPLIER" -> "供应商档案";
            case "SALE_ORDER" -> "销售订单";
            default -> "单据";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("你是单据识别助手。请从图片中提取").append(bizDesc).append("信息，")
                .append("严格输出一个 JSON 对象，不要任何 markdown 代码块、不要 ```json 包裹、不要解释文字。\n");
        sb.append("JSON 字段如下（未能识别的字段填 null，items 无明细时返回空数组）：\n");
        sb.append("{\n");
        sb.append("  \"documentType\": \"").append(bizType).append("\",\n");
        sb.append("  \"documentNo\": \"单据号/订单号\",\n");
        sb.append("  \"documentDate\": \"YYYY-MM-DD\",\n");
        sb.append("  \"orderNo\": \"关联采购订单号（仅退货单需要）\",\n");
        sb.append("  \"returnTime\": \"退货时间 YYYY-MM-DD（仅退货单需要）\",\n");
        sb.append("  \"supplierName\": \"供应商名称（采购单/供应商档案）\",\n");
        sb.append("  \"customerName\": \"客户名称（销售单）\",\n");
        sb.append("  \"accountName\": \"结算账户名称\",\n");
        sb.append("  \"contact\": \"联系人\",\n");
        sb.append("  \"mobile\": \"手机号\",\n");
        sb.append("  \"telephone\": \"固定电话\",\n");
        sb.append("  \"email\": \"邮箱\",\n");
        sb.append("  \"fax\": \"传真\",\n");
        sb.append("  \"taxNo\": \"纳税人识别号\",\n");
        sb.append("  \"taxPercent\": \"默认税率百分数（如 13）\",\n");
        sb.append("  \"bankName\": \"开户银行\",\n");
        sb.append("  \"bankAccount\": \"银行账号\",\n");
        sb.append("  \"bankAddress\": \"开户地址\",\n");
        sb.append("  \"remark\": \"备注\",\n");
        sb.append("  \"discountPercent\": \"整单折扣百分数（销售单如有）\",\n");
        sb.append("  \"items\": [\n");
        sb.append("    {\"productName\":\"产品名称\",\"productCode\":\"产品编码\",")
                .append("\"count\":数量(数字),\"productPrice\":单价(数字),")
                .append("\"taxPercent\":税率百分数(数字),\"remark\":\"行备注\"}\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("字段名严格按上面的大小写。数字字段不要带单位或货币符号，")
                .append("count 是数量、productPrice 是单价、taxPercent 是税率百分数（13 表示 13%）。\n");
        sb.append("只输出 JSON 本身，不要任何前后缀文字。");
        return sb.toString();
    }

    /** 剥 markdown fence + 解析为 ErpRecognitionDocument + 附加 ocrText/ocrProvider */
    private String buildResponse(String rawContent, String bizType) {
        String jsonText = extractJsonObject(rawContent);
        Map<String, Object> documentMap;
        try {
            documentMap = JsonUtils.parseObject(jsonText, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[buildResponse][Qwen-VL 输出非 JSON，len={}] {}", rawContent.length(),
                    truncate(rawContent, 200));
            throw new IllegalStateException("Qwen-VL 输出无法解析为 JSON：" + truncate(rawContent, 500), e);
        }
        if (documentMap == null) {
            documentMap = new LinkedHashMap<>();
        }
        documentMap.putIfAbsent("documentType", bizType);
        documentMap.putIfAbsent("items", new ArrayList<>());
        documentMap.put("finalConfidence", DEFAULT_CONFIDENCE);
        Map<String, Object> response = new LinkedHashMap<>(documentMap);
        response.put("ocrText", rawContent);
        response.put("ocrProvider", "QWEN_VISION");
        return JsonUtils.toJsonString(response);
    }

    /** 取首 { 到末 } 子串，剥 markdown ```json fence */
    private static String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private static String guessMediaType(Path image) {
        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
