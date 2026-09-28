package cn.iocoder.txgy.module.ai.api.customer;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.ratelimiter.core.redis.RateLimiterRedisDAO;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyReqDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCustomerServiceApiImplTest {

    private DeepSeekChatModel chatModel;
    private RateLimiterRedisDAO rateLimiter;
    private AiCustomerServiceApiImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        chatModel = mock(DeepSeekChatModel.class);
        rateLimiter = mock(RateLimiterRedisDAO.class);
        service = new AiCustomerServiceApiImpl(chatModel, rateLimiter);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void replyAppliesAccountAndConversationQuotas() {
        when(rateLimiter.tryAcquire(any(), anyInt(), eq(1), eq(TimeUnit.MINUTES))).thenReturn(true);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage(
                "{\"reply\":\"请以商品页实时库存为准。\",\"recommendedProductIds\":[12,12,13,14,15]}"));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        var result = service.reply(request()).getData();

        assertEquals("请以商品页实时库存为准。", result.getReply());
        assertEquals(List.of(12L, 13L, 14L), result.getRecommendedProductIds());
        verify(rateLimiter).tryAcquire("ai-customer-service:account:2009:316", 20, 1, TimeUnit.MINUTES);
        verify(rateLimiter).tryAcquire("ai-customer-service:conversation:2009:conversation_20260922", 10, 1,
                TimeUnit.MINUTES);
    }

    @Test
    void accountQuotaRejectsBeforeCallingProvider() {
        when(rateLimiter.tryAcquire("ai-customer-service:account:2009:316", 20, 1, TimeUnit.MINUTES))
                .thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.reply(request()));

        assertEquals("AI客服请求过于频繁，请稍后再试", error.getMessage());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private static AiCustomerServiceReplyReqDTO request() {
        AiCustomerServiceReplyReqDTO request = new AiCustomerServiceReplyReqDTO();
        request.setScope("SHOP");
        request.setShopId(12L);
        request.setRequesterId(316L);
        request.setConversationId("conversation_20260922");
        request.setQuestion("这个商品有库存吗？");
        request.setContext("可售商品候选：12、13、14");
        return request;
    }
}
