package cn.iocoder.txgy.module.ai.api.customer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ratelimiter.core.redis.RateLimiterRedisDAO;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyReqDTO;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Internal mall customer-service inference endpoint. */
@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
public class AiCustomerServiceApiImpl implements AiCustomerServiceApi {

    private static final String MODEL = "deepseek-chat";
    private static final String SYSTEM_PROMPT = """
            你是防腐保温工业购物平台的店铺 AI 客服。请使用简洁、专业的中文回答，最多 120 个汉字。
            只能依据用户问题和已提供的店铺上下文作答。不得编造价格、库存、物流、支付、退款、资质或售后结果；
            涉及实时业务状态时，引导用户以页面实际信息为准或转人工客服。不得索要密码、验证码、支付口令或密钥。
            如果上下文含“可售商品候选”，用户要求推荐商品时，只能从候选编号中选择最多 3 个；不能虚构编号。
            必须只输出 JSON：{"reply":"给用户的答复","recommendedProductIds":[候选商品编号]}。
            用户没有要求推荐商品时 recommendedProductIds 返回空数组。如果上下文不足，请明确说明并建议转人工，不要猜测。
            """;

    private record ModelReply(String reply, List<Long> recommendedProductIds) {}

    private static final int ACCOUNT_REQUESTS_PER_MINUTE = 20;
    private static final int CONVERSATION_REQUESTS_PER_MINUTE = 10;

    private final DeepSeekChatModel deepSeekChatModel;
    private final RateLimiterRedisDAO rateLimiterRedisDAO;

    @Override
    public CommonResult<AiCustomerServiceReplyRespDTO> reply(AiCustomerServiceReplyReqDTO request) {
        if ("SHOP".equals(request.getScope()) && request.getShopId() <= 0
                || "PLATFORM".equals(request.getScope()) && request.getShopId() != 0) {
            throw new IllegalArgumentException("Invalid customer-service scope");
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        acquireQuota("account:" + tenantId + ':' + request.getRequesterId(), ACCOUNT_REQUESTS_PER_MINUTE);
        acquireQuota("conversation:" + tenantId + ':' + request.getConversationId(),
                CONVERSATION_REQUESTS_PER_MINUTE);
        String userPrompt = "咨询范围：" + request.getScope()
                + (request.getShopId() == null ? "" : "，店铺编号：" + request.getShopId())
                + "\n可用上下文：\n" + StrUtil.blankToDefault(request.getContext(), "无")
                + "\n用户问题：\n" + request.getQuestion().strip();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(MODEL).temperature(0.1).maxTokens(320).build();
        ChatResponse response = deepSeekChatModel.call(new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt)), options));
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        Integer totalTokens = usage == null ? null : usage.getTotalTokens();
        log.info("[reply][AI客服调用完成 tenantId({}) requesterId({}) conversationId({}) scope({}) shopId({})"
                        + " promptTokens({}) completionTokens({}) totalTokens({})]",
                tenantId, request.getRequesterId(), request.getConversationId(), request.getScope(),
                request.getShopId(), promptTokens, completionTokens, totalTokens);
        String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        if (StrUtil.isBlank(text)) {
            throw new IllegalStateException("AI customer-service response is empty");
        }
        ModelReply parsed = parseReply(text);
        if (parsed == null || StrUtil.isBlank(parsed.reply())) {
            throw new IllegalStateException("AI customer-service response format is invalid");
        }
        String reply = StrUtil.subWithLength(parsed.reply().strip(), 0, 1000);
        List<Long> ids = parsed.recommendedProductIds() == null ? List.of() : parsed.recommendedProductIds().stream()
                .filter(Objects::nonNull).filter(id -> id > 0).distinct().limit(3).toList();
        return CommonResult.success(new AiCustomerServiceReplyRespDTO(reply, MODEL, ids));
    }

    private void acquireQuota(String key, int count) {
        if (!Boolean.TRUE.equals(rateLimiterRedisDAO.tryAcquire(
                "ai-customer-service:" + key, count, 1, TimeUnit.MINUTES))) {
            throw new ServiceException(429, "AI客服请求过于频繁，请稍后再试");
        }
    }

    private static ModelReply parseReply(String value) {
        String text = value.strip();
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return JsonUtils.parseObjectQuietly(text.substring(start, end + 1), ModelReply.class);
    }
}
