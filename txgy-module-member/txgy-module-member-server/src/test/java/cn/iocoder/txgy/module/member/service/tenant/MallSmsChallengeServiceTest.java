package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import cn.iocoder.txgy.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.*;
import static cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.Purpose.*;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallSmsChallengeServiceTest {
    private MallTenantAuthProperties properties; private MallAuthPolicy policy; private StringRedisTemplate redis;
    private SmsSendApi sender; private MallSmsChallengeService service;
    private final List<List<String>> keyCalls = new ArrayList<>();
    private final List<List<String>> argCalls = new ArrayList<>();
    @BeforeEach void setup() {
        properties = new MallTenantAuthProperties(); properties.setHmacSecret("isolated-test-secret-never-use-in-production"); properties.setSmsTemplateCode("isolated");
        policy = mock(MallAuthPolicy.class); redis = mock(StringRedisTemplate.class); sender = mock(SmsSendApi.class);
        service = new MallSmsChallengeService(properties, policy, redis, sender);
        when(policy.resolve(any(), anyBoolean())).thenReturn(9001L);
        when(sender.sendSingleSmsToMember(any())).thenReturn(CommonResult.success(9L));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(invocation -> {
            keyCalls.add(List.copyOf(invocation.getArgument(1)));
            List<String> args = new ArrayList<>(); for (int i = 2; i < invocation.getArguments().length; i++) args.add(String.valueOf(invocation.getArguments()[i]));
            argCalls.add(args); return 1L;
        });
        TenantContextHolder.clear();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void sendUsesOwnHashOnlyNamespaceAndDoesNotClaimDelivered() {
        var result = service.send(9001L, "13900009991", REGISTER, "peer");
        assertTrue(result.challengeId().matches("[a-f0-9]{32}")); assertEquals("SUBMITTED_UNCONFIRMED", result.deliveryStatus());
        assertEquals(300, result.expiresIn());
        var sent = ArgumentCaptor.forClass(SmsSendSingleToUserReqDTO.class); verify(sender).sendSingleSmsToMember(sent.capture());
        String code = sent.getValue().getTemplateParams().get("code").toString(); assertTrue(code.matches("[0-9]{6}"));
        assertTrue(keyCalls.stream().flatMap(List::stream).noneMatch(k -> k.contains("13900009991") || k.contains("peer" + ":peer")));
        assertTrue(argCalls.get(0).stream().noneMatch(v -> v.equals(code) || v.equals("13900009991")));
        assertNull(sent.getValue().getUserId());
        assertEquals(64, argCalls.get(0).get(1).length()); assertEquals(64, argCalls.get(0).get(2).length());
    }
    @Test void sendScopeIsTrustedAndRestoredEvenUnderIgnoredControllerContext() {
        TenantContextHolder.setTenantId(9002L); TenantContextHolder.setIgnore(true);
        when(sender.sendSingleSmsToMember(any())).thenAnswer(invocation -> {
            assertEquals(9001L, TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            return CommonResult.success(9L);
        });
        service.send(9001L, "13900009991", REGISTER, "peer");
        assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void rateLimitRejectsBeforeAnySmsCall() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1L);
        assertEquals(MALL_AUTH_RATE_LIMITED.getCode(), assertThrows(ServiceException.class,
                () -> service.send(9001L, "13900009991", REGISTER, "peer")).getCode());
        verifyNoInteractions(sender);
    }
    @Test void sendFailureInvalidatesChallengeWithoutReleasingBudget() {
        when(sender.sendSingleSmsToMember(any())).thenThrow(new IllegalStateException("timeout"));
        assertEquals(MALL_AUTH_SEND_FAILED.getCode(), assertThrows(ServiceException.class,
                () -> service.send(9001L, "13900009991", REGISTER, "peer")).getCode());
        assertEquals(2, keyCalls.size()); assertEquals(keyCalls.get(0).subList(0, 2), keyCalls.get(1));
    }
    @Test void expiredReadyTransitionDoesNotReturnUsableChallenge() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 0L, 1L);
        assertThrows(ServiceException.class, () -> service.send(9001L, "13900009991", REGISTER, "peer"));
    }
    @Test void resendPhoneAndIpLimitsSpanTenantsAndPurposesButBindingsDiffer() {
        service.send(9001L, "13900009991", REGISTER, "peer");
        when(policy.resolve(9002L, false)).thenReturn(9002L);
        service.send(9002L, "13900009991", SMS_LOGIN, "peer");
        assertEquals(keyCalls.get(0).subList(2, 5), keyCalls.get(2).subList(2, 5));
        assertNotEquals(keyCalls.get(0).get(1), keyCalls.get(2).get(1));
    }
    @Test void consumeRevalidatesPolicyAndUsesEndpointPurposeNotClientChoice() {
        assertEquals(9001L, service.consume(9001L, "13900009991", RESET_PASSWORD, "a".repeat(32), "123456", "peer"));
        verify(policy).resolve(9001L, false); verifyNoInteractions(sender);
    }
    @Test void mismatchExpiredOrUsedConsumeGetsSamePublicError() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertEquals(MALL_AUTH_CODE_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.consume(9001L, "13900009991", REGISTER, "a".repeat(32), "123456", "peer")).getCode());
    }
    @Test void malformedChallengeCannotFormRedisKey() {
        assertThrows(ServiceException.class, () -> service.consume(9001L, "13900009991", REGISTER, "../other", "123456", "peer"));
        verifyNoInteractions(redis, sender);
    }
}
