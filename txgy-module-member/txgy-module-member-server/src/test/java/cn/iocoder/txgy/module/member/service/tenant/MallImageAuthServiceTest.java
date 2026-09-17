package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MallImageAuthServiceTest {
    @Mock private MallAccountPolicy policy;
    @Mock private MallImageChallengeService challenges;
    @Mock private MemberAuthService auth;
    @InjectMocks private MallImageAuthService service;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9002L);
        TenantContextHolder.setIgnore(true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void issueUsesAuthorizedTenantAndNormalizedNameWithoutAnyAccountCreation() {
        AppMallImageAuthReqVO.Image body = new AppMallImageAuthReqVO.Image();
        body.setTenantId(9001L); body.setLoginName(" Buyer_1 "); body.setCaptchaSession("b".repeat(32));
        body.setPreviousChallengeId("a".repeat(32)); body.setPurpose("REGISTER");
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        MallImageChallengeService.Challenge result = new MallImageChallengeService.Challenge(9001L,
                "c".repeat(32), "data:image/png;base64,aGVsbG8=", 300, 2);
        when(challenges.issue(9001L, "buyer_1", body.getCaptchaSession(), body.getPreviousChallengeId(), "REGISTER", "peer"))
                .thenReturn(result);
        assertSame(result, service.issue(body, "peer"));
        verifyNoInteractions(auth);
    }

    @Test void successfulConsumePrecedesOnlyImageRegistrationAndRestoresTenantContext() {
        AppMallImageAuthReqVO.Register body = registration();
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        AppAuthLoginRespVO expected = new AppAuthLoginRespVO().setTenantId(9001L).setUserId(77L);
        when(auth.registerWithVerifiedImage(body)).thenAnswer(call -> {
            assertEquals(9001L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            assertEquals("buyer_1", body.getLoginName());
            assertEquals("supplier", body.getMemberIdentity());
            return expected;
        });
        assertSame(expected, service.register(body, "peer"));
        var order = inOrder(policy, challenges, auth);
        order.verify(policy).resolve(9001L, true);
        order.verify(challenges).consume(9001L, "buyer_1", body.getCaptchaSession(), body.getImageChallengeId(), "AB2CD", "peer");
        order.verify(auth).registerWithVerifiedImage(body);
        verifyNoMoreInteractions(auth, challenges);
        assertEquals(9002L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void failedConsumePreventsRegistrationAndTokenCreation() {
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        doThrow(exception(MallImageChallengeService.INVALID)).when(challenges)
                .consume(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(MallImageChallengeService.INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.register(registration(), "peer")).getCode());
        verifyNoInteractions(auth);
        assertEquals(9002L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void policyFailureCannotConsumeOrCreateAnything() {
        when(policy.resolve(9001L, true)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        assertThrows(ServiceException.class, () -> service.register(registration(), "peer"));
        verifyNoInteractions(challenges, auth);
    }

    @Test void invalidNameCannotReachChallengeConsumption() {
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        AppMallImageAuthReqVO.Register body = registration(); body.setLoginName("19900000000");
        assertEquals(MallAccountPolicy.INVALID_ACCOUNT.getCode(),
                assertThrows(ServiceException.class, () -> service.register(body, "peer")).getCode());
        verifyNoInteractions(challenges, auth);
    }

    @Test void agreementAndPasswordConfirmationAreCheckedBeforeConsume() {
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        AppMallImageAuthReqVO.Register body = registration(); body.setAgree(false);
        assertThrows(ServiceException.class, () -> service.register(body, "peer"));
        body.setAgree(true); body.setConfirmPassword("Different123");
        assertThrows(ServiceException.class, () -> service.register(body, "peer"));
        verifyNoInteractions(challenges, auth);
    }

    @Test void duplicateDatabaseKeyMapsToAccountUsedWithoutRestoringOrReissuingImage() {
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        when(auth.registerWithVerifiedImage(any())).thenThrow(new DuplicateKeyException("private SQL detail"));
        AppMallImageAuthReqVO.Register body = registration();
        ServiceException error = assertThrows(ServiceException.class, () -> service.register(body, "peer"));
        assertEquals(MallAccountPolicy.ACCOUNT_USED.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("private SQL"));
        verify(challenges).consume(9001L, "buyer_1", body.getCaptchaSession(), body.getImageChallengeId(), "AB2CD", "peer");
        verifyNoMoreInteractions(challenges);
        verify(auth, times(1)).registerWithVerifiedImage(body);
        assertEquals(9002L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void transactionFailureDoesNotRetryAuthOrResurrectConsumedImage() {
        when(policy.resolve(9001L, true)).thenReturn(9001L);
        when(auth.registerWithVerifiedImage(any())).thenThrow(new IllegalStateException("transaction failed"));
        AppMallImageAuthReqVO.Register body = registration();
        assertThrows(IllegalStateException.class, () -> service.register(body, "peer"));
        verify(challenges).consume(9001L, "buyer_1", body.getCaptchaSession(), body.getImageChallengeId(), "AB2CD", "peer");
        verifyNoMoreInteractions(challenges);
        verify(auth, times(1)).registerWithVerifiedImage(body);
        assertEquals(9002L, TenantContextHolder.getTenantId());
    }

    @Test void passwordLoginUsesNormalizedAccountAndGlobalBudgetBeforeAuthentication() {
        AppMallImageAuthReqVO.Login body = login();
        when(policy.resolve(9001L, false)).thenReturn(9001L);
        when(auth.loginWithAccountPassword("buyer_1", "1234")).thenAnswer(call -> {
            assertEquals(9001L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return new AppAuthLoginRespVO().setTenantId(9001L).setUserId(77L);
        });
        assertEquals(77L, service.login(body, "peer").getUserId());
        var order = inOrder(policy, challenges, auth);
        order.verify(policy).resolve(9001L, false);
        order.verify(challenges).limitPasswordAttempts("buyer_1", "peer");
        order.verify(auth).loginWithAccountPassword("buyer_1", "1234");
        assertEquals(9002L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void exhaustedPasswordBudgetNeverCallsAuthentication() {
        when(policy.resolve(9001L, false)).thenReturn(9001L);
        doThrow(exception(MallImageChallengeService.LIMITED)).when(challenges).limitPasswordAttempts("buyer_1", "peer");
        assertEquals(MallImageChallengeService.LIMITED.getCode(),
                assertThrows(ServiceException.class, () -> service.login(login(), "peer")).getCode());
        verifyNoInteractions(auth);
    }

    private static AppMallImageAuthReqVO.Register registration() {
        AppMallImageAuthReqVO.Register body = new AppMallImageAuthReqVO.Register();
        body.setTenantId(9001L); body.setLoginName(" Buyer_1 "); body.setPassword("Test1234"); body.setConfirmPassword("Test1234");
        body.setCaptchaSession("b".repeat(32)); body.setImageChallengeId("a".repeat(32)); body.setImageCode("AB2CD");
        body.setAgree(true); body.setMemberIdentity("supplier"); return body;
    }
    private static AppMallImageAuthReqVO.Login login() {
        AppMallImageAuthReqVO.Login body = new AppMallImageAuthReqVO.Login();
        body.setTenantId(9001L); body.setAccount(" Buyer_1 "); body.setPassword("1234"); return body;
    }
}
