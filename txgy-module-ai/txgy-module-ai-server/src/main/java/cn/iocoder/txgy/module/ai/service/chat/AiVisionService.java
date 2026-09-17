package cn.iocoder.txgy.module.ai.service.chat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.iocoder.txgy.module.ai.service.model.AiApiKeyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AI 视觉识别服务：调用视觉大模型，把图片中的单据内容识别为「字段:值」文本
 *
 * 用途：用户在 AI 助手上传图片（如手写/打印的工单、报工单照片）时，
 * 先由本服务识别出字段文本，再交给对话模型提取字段并调用录入工具写入 MES 真实数据库。
 *
 * 多厂商自动回退：按下方 PROVIDERS 顺序尝试所有「已启用密钥」的视觉厂商，
 * 任一家成功即返回；全部失败返回 null（调用方降级为"图片识别失败"提示）。
 * 用户只需在「AI 控制台-API 密钥管理」配置任一视觉厂商的有效密钥即可，无需改代码。
 * 推荐智谱 glm-4v-flash（免费）。
 *
 * @author 天信管业
 */
@Slf4j
@Service
public class AiVisionService {

    /** 单图识别提示词：输出「字段:值」列表，供对话模型直接提取录入 */
    private static final String EXTRACT_PROMPT =
            "你是 MES 单据字段识别助手。请识别图片中的生产工单、生产报工、检验单等单据信息，"
                    + "按「字段:值」格式每行一条输出（例如：工单名称:xxx、产品:xxx、计划数量:xxx、交期:xxx、批次号:xxx）。"
                    + "图片中不存在或无法看清的字段输出「字段名:无法识别」。只输出字段列表，不要任何解释或多余文字。";

    /** 视觉厂商调用配置：平台枚举名 → 兼容接口地址 + 视觉模型 */
    private static final List<String[]> PROVIDERS = List.of(
            new String[]{"TongYi", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-vl-plus"},
            new String[]{"ZhiPu", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4v-flash"},
            new String[]{"SiliconFlow", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-VL-32B-Instruct"},
            new String[]{"Moonshot", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k-vision-preview"});

    @Resource
    private AiApiKeyService apiKeyService;

    /**
     * 识别图片中的单据字段内容（多厂商自动回退）
     *
     * @param imageUrl 图片访问地址（AI 附件 URL，服务端可下载）
     * @return 「字段:值」文本；全部厂商失败返回 null
     */
    public String extractImageText(String imageUrl) {
        // 1. 下载图片并转 Base64（data URL 形式传给视觉模型）
        // 附件 URL 面向浏览器生成（宿主机回环地址），AI 容器内需改写为 Compose 网络内的网关地址
        String downloadUrl = imageUrl
                .replace("127.0.0.1:48080", "txgy-slim-gateway-1:48080")
                .replace("localhost:48080", "txgy-slim-gateway-1:48080");
        byte[] imageBytes;
        try {
            imageBytes = HttpUtil.downloadBytes(downloadUrl);
        } catch (Exception e) {
            log.error("[extractImageText][图片下载失败 url({})]", downloadUrl, e);
            return null;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("[extractImageText][图片下载为空 url({})]", downloadUrl);
            return null;
        }
        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
        // 2. 逐厂商尝试：取该平台已启用的密钥调用，任一成功即返回
        for (String[] provider : PROVIDERS) {
            String platform = provider[0];
            String endpoint = provider[1];
            String model = provider[2];
            String apiKey = resolveApiKey(platform);
            if (StrUtil.isBlank(apiKey)) {
                continue; // 该平台无启用密钥，跳过
            }
            try {
                String text = callVisionModel(endpoint, model, apiKey, dataUrl);
                if (StrUtil.isNotBlank(text)) {
                    log.info("[extractImageText][识别成功 platform({}) model({}) 文本长度({})]",
                            platform, model, text.length());
                    return text.trim();
                }
                log.warn("[extractImageText][识别为空 platform({}) model({})]", platform, model);
            } catch (Exception e) {
                // 单个厂商失败（密钥无效/模型不可用等）不影响后续厂商尝试
                log.warn("[extractImageText][识别失败 platform({}) model({}) reason({})]",
                        platform, model, e.getMessage());
            }
        }
        log.warn("[extractImageText][所有视觉厂商均失败，图片识别跳过 url({})]", imageUrl);
        return null;
    }

    /**
     * 调用单个视觉厂商（OpenAI 兼容多模态格式）
     */
    private String callVisionModel(String endpoint, String model, String apiKey, String dataUrl) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        JSONArray content = new JSONArray();
        JSONObject imagePart = new JSONObject();
        imagePart.set("type", "image_url");
        imagePart.set("image_url", new JSONObject().set("url", dataUrl));
        content.add(imagePart);
        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", EXTRACT_PROMPT);
        content.add(textPart);
        userMessage.set("content", content);
        messages.add(userMessage);
        body.set("messages", messages);
        String resp = HttpRequest.post(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60_000)
                .execute()
                .body();
        if (StrUtil.isBlank(resp)) {
            throw new IllegalStateException("响应为空");
        }
        JSONObject respJson = JSONUtil.parseObj(resp);
        if (respJson.containsKey("error")) {
            // 密钥无效/模型不可用等：抛出厂商错误信息，由外层尝试下一家
            throw new IllegalStateException(String.valueOf(respJson.getJSONObject("error").get("message")));
        }
        JSONArray choices = respJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("响应无 choices");
        }
        return choices.getJSONObject(0).getJSONObject("message").getStr("content");
    }

    /**
     * 取指定平台已启用的 API Key（密钥是平台级全局资源，忽略租户过滤）
     */
    private String resolveApiKey(String platform) {
        List<AiApiKeyDO> keys = TenantUtils.executeIgnore(() -> apiKeyService.getApiKeyList());
        if (keys == null) {
            return null;
        }
        List<AiApiKeyDO> hits = new ArrayList<>();
        for (AiApiKeyDO key : keys) {
            if (platform.equals(key.getPlatform()) && Integer.valueOf(0).equals(key.getStatus())) {
                hits.add(key);
            }
        }
        return hits.isEmpty() ? null : hits.get(0).getApiKey();
    }

}
