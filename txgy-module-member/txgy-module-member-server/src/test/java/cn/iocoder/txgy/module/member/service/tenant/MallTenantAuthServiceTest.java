package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.*;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.Purpose.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallTenantAuthServiceTest {
    private MallAuthPolicy policy; private MallSmsChallengeService challenges; private MemberAuthService auth;
    private MemberUserMapper users; private PasswordEncoder passwords; private OAuth2TokenCommonApi tokens;
    private MallTenantAuthService service;
    @BeforeEach void setup() {
        policy = mock(MallAuthPolicy.class); challenges = mock(MallSmsChallengeService.class); auth = mock(MemberAuthService.class);
        users = mock(MemberUserMapper.class); passwords = mock(PasswordEncoder.class); tokens = mock(OAuth2TokenCommonApi.class);
        service = new MallTenantAuthService(policy, challenges, auth, users, passwords, tokens);
        TenantContextHolder.setTenantId(9002L); TenantContextHolder.setIgnore(true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private Register registration() {
        Register r = new Register(); r.setTenantId(9001L); r.setMobile("13900009991"); r.setCode("123456"); r.setChallengeId("a".repeat(32));
        r.setNickname("test"); r.setPassword("Test1234"); r.setAgree(true); r.setMemberIdentity("supplier"); return r;
    }
    @Test void registrationConsumesOnlyRegisterPurposeAndNeverCallsLegacySms() {
        Register r = registration();
        when(challenges.consume(9001L, r.getMobile(), REGISTER, r.getChallengeId(), r.getCode(), "peer")).thenReturn(9001L);
        when(auth.registerWithVerifiedMobile(any())).thenAnswer(invocation -> {
            assertEquals(9001L, TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            return new AppAuthLoginRespVO().setTenantId(9001L).setUserId(77L);
        });
        assertEquals(9001L, service.register(r, "peer").getTenantId());
        verify(auth, never()).register(any()); verifyNoInteractions(users, tokens);
        assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void failedConsumePreventsAllAccountWritesAndTokenCreation() {
        when(challenges.consume(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("invalid"));
        assertThrows(IllegalStateException.class, () -> service.register(registration(), "peer"));
        verifyNoInteractions(auth, users, passwords, tokens);
    }
    @Test void duplicateAndSoftDeletedUniqueKeysReturnDomainError() {
        when(challenges.consume(any(), any(), any(), any(), any(), any())).thenReturn(9001L);
        when(auth.registerWithVerifiedMobile(any())).thenThrow(new DuplicateKeyException("secret SQL"));
        assertThrows(ServiceException.class, () -> service.register(registration(), "peer"));
        assertEquals(9002L, TenantContextHolder.getTenantId());
    }
    @Test void smsLoginUsesExistingMemberOnlyAndDoesNotAutoRegister() {
        Register r = registration(); when(challenges.consume(any(), any(), eq(SMS_LOGIN), any(), any(), any())).thenReturn(9001L);
        service.smsLogin(r, "peer"); verify(auth).loginWithVerifiedMobile(r.getMobile()); verify(auth, never()).smsLogin(any());
    }
    @Test void passwordLoginCannotMassAssignSocialBinding() {
        PasswordLogin r = new PasswordLogin(); r.setTenantId(9001L); r.setMobile("13900009991"); r.setPassword("Test1234");
        when(policy.resolve(9001L, false)).thenReturn(9001L);
        when(auth.login(any())).thenAnswer(invocation -> {
            cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginReqVO vo = invocation.getArgument(0);
            assertNull(vo.getSocialType()); assertEquals(9001L, TenantContextHolder.getTenantId()); return null;
        });
        service.passwordLogin(r, "peer"); verify(challenges).limitPasswordAttempts(r.getMobile(), "peer");
    }
    private ResetPassword reset() {
        ResetPassword r = new ResetPassword(); r.setTenantId(9001L); r.setMobile("13900009991");
        r.setCode("123456"); r.setChallengeId("a".repeat(32)); r.setPassword("Test5678"); return r;
    }
    @Test void resetRequiresResetPurposeAndRevokesOnlyThatMember() {
        ResetPassword r = reset(); when(challenges.consume(any(), any(), eq(RESET_PASSWORD), any(), any(), any())).thenReturn(9001L);
        when(users.selectByMobile(r.getMobile())).thenReturn(member(9001L));
        when(passwords.encode(r.getPassword())).thenReturn("hashed"); when(users.updateById(any(MemberUserDO.class))).thenReturn(1);
        when(tokens.removeAccessToken(77L, 1)).thenReturn(CommonResult.success(true));
        service.resetPassword(r, "peer"); verify(tokens).removeAccessToken(77L, 1);
        verify(users).updateById(argThat((MemberUserDO u) -> u.getId() == 77L && "hashed".equals(u.getPassword())));
    }
    @Test void missingOrForeignMemberCannotResetPassword() {
        when(challenges.consume(any(), any(), any(), any(), any(), any())).thenReturn(9001L);
        assertThrows(ServiceException.class, () -> service.resetPassword(reset(), "peer"));
        when(users.selectByMobile(anyString())).thenReturn(member(9002L));
        assertThrows(ServiceException.class, () -> service.resetPassword(reset(), "peer"));
        verifyNoInteractions(passwords, tokens);
    }
    private MemberUserDO member(long tenant) {
        MemberUserDO user = new MemberUserDO().setId(77L).setStatus(0); user.setTenantId(tenant); return user;
    }
}
