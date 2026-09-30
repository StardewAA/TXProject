package cn.iocoder.txgy.module.member.service.auth;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.*;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy;
import cn.iocoder.txgy.module.member.service.tenant.MallCustomerAffiliationService;
import cn.iocoder.txgy.module.member.service.tenant.MallImageChallengeService;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import cn.iocoder.txgy.module.system.api.logger.LoginLogApi;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.SocialUserApi;
import cn.iocoder.txgy.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.txgy.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.txgy.module.system.enums.logger.LoginResultEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberImageAccountTest {
    @Mock private MemberUserService userService;
    @Mock private MemberUserMapper memberUserMapper;
    @Mock private SmsCodeApi smsCodeApi;
    @Mock private LoginLogApi loginLogApi;
    @Mock private SocialUserApi socialUserApi;
    @Mock private SocialClientApi socialClientApi;
    @Mock private OAuth2TokenCommonApi oauth2TokenApi;
    @Mock private MallCustomerAffiliationService customerAffiliations;
    @Mock private MallAccountPolicy accountPolicy;
    @Mock private MallImageChallengeService imageChallenges;
    @Mock private MemberPlatformAccountService platformAccounts;
    @InjectMocks private MemberAuthServiceImpl service;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9001L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.addHeader("terminal", "20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        lenient().when(accountPolicy.isEnabled()).thenReturn(true);
        lenient().when(loginLogApi.createLoginLog(any())).thenReturn(CommonResult.success(true));
    }
    @AfterEach void clear() {
        TenantContextHolder.clear(); RequestContextHolder.resetRequestAttributes();
    }

    @Test void imageRegistrationPreservesMembershipContactAndAffiliationBeforeIssuingTokens() {
        AppMallImageAuthReqVO.Register body = registration();
        MemberUserDO member = member(9001L).setMemberIdentity("supplier").setContactMobile("13900009991");
        when(userService.registerAccountUser(eq("buyer_1"), eq("Test1234"), eq("图片会员"),
                eq("13900009991"), eq("supplier"), anyString(), any())).thenReturn(member);
        configureTokens();
        AppAuthLoginRespVO result = service.registerWithVerifiedImage(body);
        assertIdentity(result);
        assertEquals("supplier", member.getMemberIdentity());
        assertEquals("13900009991", member.getContactMobile());
        assertNull(member.getMobile());
        var order = inOrder(userService, customerAffiliations, platformAccounts, oauth2TokenApi);
        order.verify(userService).registerAccountUser(eq("buyer_1"), eq("Test1234"), eq("图片会员"),
                eq("13900009991"), eq("supplier"), anyString(), any());
        order.verify(customerAffiliations).bindRegistration(81L);
        order.verify(platformAccounts).createForRegistration(member, 9001L);
        order.verify(oauth2TokenApi).createAccessToken(argThat(token -> token.getUserId().equals(81L)
                && token.getUserType().equals(UserTypeEnum.MEMBER.getValue())));
        verifyNoInteractions(smsCodeApi, socialUserApi, memberUserMapper);
        assertNoLegacyCreation();
    }

    @Test void registrationWithoutPhoneDoesNotInventVerifiedMobileOrRequireSms() {
        AppMallImageAuthReqVO.Register body = registration(); body.setContactMobile(null); body.setNickname(null);
        when(userService.registerAccountUser(eq("buyer_1"), eq("Test1234"), isNull(), isNull(), eq("supplier"), anyString(), any()))
                .thenReturn(member(9001L));
        configureTokens(); assertIdentity(service.registerWithVerifiedImage(body));
        verifyNoInteractions(smsCodeApi);
        assertNoLegacyCreation();
    }

    @Test void newlyCreatedAccountReloadsPersistedTenantBeforeIssuingToken() {
        stubRegistration(member(null));
        when(userService.getUser(81L)).thenReturn(member(9001L));
        configureTokens(); assertIdentity(service.registerWithVerifiedImage(registration()));
        verify(userService).getUser(81L);
    }

    @Test void failedAffiliationCannotIssueLoginTokensOrRetryRegistration() {
        stubRegistration(member(9001L));
        doThrow(new IllegalStateException("affiliation unavailable")).when(customerAffiliations).bindRegistration(81L);
        assertThrows(IllegalStateException.class, () -> service.registerWithVerifiedImage(registration()));
        verify(userService, times(1)).registerAccountUser(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(oauth2TokenApi, loginLogApi, smsCodeApi);
    }

    @Test void failedPlatformMappingCannotIssueTokenAfterCreatingMember() {
        MemberUserDO member = member(9001L);
        stubRegistration(member);
        doThrow(new IllegalStateException("platform mapping failed")).when(platformAccounts)
                .createForRegistration(member, 9001L);

        assertThrows(IllegalStateException.class, () -> service.registerWithVerifiedImage(registration()));

        verify(customerAffiliations).bindRegistration(81L);
        verify(platformAccounts).createForRegistration(member, 9001L);
        verifyNoInteractions(oauth2TokenApi, loginLogApi);
    }

    @Test void pictureFeatureOffCannotCreateAccountsOrUseAccountPasswordEndpoint() {
        when(accountPolicy.isEnabled()).thenReturn(false);
        assertEquals(MALL_TENANT_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.registerWithVerifiedImage(registration())).getCode());
        assertEquals(MALL_TENANT_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verifyNoInteractions(userService, memberUserMapper, customerAffiliations, platformAccounts, oauth2TokenApi, smsCodeApi);
    }

    @Test void loginNameUsesCanonicalLookupAndExistingPasswordHash() {
        configureAccount(member(9001L)); configureTokens();
        assertIdentity(service.loginWithAccountPassword(" BuYeR_1 ", "1234"));
        verify(memberUserMapper).selectByLoginName("buyer_1");
        verify(userService).isPasswordMatch("1234", "existing-hash");
        verify(userService, never()).getUserByMobile(anyString());
        verifyNoInteractions(customerAffiliations, smsCodeApi);
        assertNoLegacyCreation();
    }

    @Test void oldMobileAccountKeepsItsMemberIdentityAndFourCharacterPassword() {
        when(userService.getUserByMobile("13900009991")).thenReturn(member(9001L).setMobile("13900009991").setLoginName(null));
        when(userService.isPasswordMatch("1234", "existing-hash")).thenReturn(true);
        configureTokens(); assertIdentity(service.loginWithAccountPassword(" 13900009991 ", "1234"));
        verifyNoInteractions(memberUserMapper, customerAffiliations, smsCodeApi);
        assertNoLegacyCreation();
    }

    @Test void legacyPasswordEndpointUsesTheSameImageEraAccountAndPeerBudget() {
        when(userService.getUserByMobile("13900009991")).thenReturn(member(9001L).setMobile("13900009991"));
        when(userService.isPasswordMatch("1234", "existing-hash")).thenReturn(true);
        configureTokens();
        assertIdentity(service.login(new AppAuthLoginReqVO().setMobile("13900009991").setPassword("1234")));
        var order = inOrder(imageChallenges, userService);
        order.verify(imageChallenges).limitPasswordAttempts("13900009991", "127.0.0.1");
        order.verify(userService).getUserByMobile("13900009991");
    }

    @Test void legacyPasswordEndpointCannotBypassAnExhaustedBudget() {
        doThrow(exception(MallImageChallengeService.LIMITED)).when(imageChallenges)
                .limitPasswordAttempts("13900009991", "127.0.0.1");
        assertEquals(MallImageChallengeService.LIMITED.getCode(), assertThrows(ServiceException.class,
                () -> service.login(new AppAuthLoginReqVO().setMobile("13900009991").setPassword("1234"))).getCode());
        verifyNoInteractions(userService, memberUserMapper, oauth2TokenApi);
    }

    @Test void legacyPasswordEndpointKeepsCompatibilityWhenImageFeatureIsDisabled() {
        when(accountPolicy.isEnabled()).thenReturn(false);
        when(userService.getUserByMobile("13900009991")).thenReturn(member(9001L).setMobile("13900009991"));
        when(userService.isPasswordMatch("1234", "existing-hash")).thenReturn(true);
        configureTokens();
        assertIdentity(service.login(new AppAuthLoginReqVO().setMobile("13900009991").setPassword("1234")));
        verifyNoInteractions(imageChallenges);
    }

    @Test void unknownLoginNameNeverFallsBackToPhoneLookupOrAutoRegistration() {
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verify(memberUserMapper).selectByLoginName("buyer_1");
        verify(userService, never()).getUserByMobile(anyString());
        verify(userService, never()).isPasswordMatch(anyString(), anyString());
        verifyNoInteractions(oauth2TokenApi, customerAffiliations);
        assertNoLegacyCreation();
    }

    @Test void unknownMobileNeverFallsBackToLoginNameOrCreatesAnotherAccount() {
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("13900009991", "1234")).getCode());
        verify(userService).getUserByMobile("13900009991"); verifyNoInteractions(memberUserMapper, oauth2TokenApi);
        assertNoLegacyCreation();
    }

    @Test void incorrectPasswordCannotIssueTokensOrChangeProfile() {
        when(memberUserMapper.selectByLoginName("buyer_1")).thenReturn(member(9001L));
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "wrong")).getCode());
        verify(userService).isPasswordMatch("wrong", "existing-hash");
        verify(oauth2TokenApi, never()).createAccessToken(any());
        verify(userService, never()).updateUserLogin(anyLong(), anyString());
    }

    @Test void platformLoginRequiresTheMappedCanonicalMemberBeforePasswordAndToken() {
        configureAccount(member(9001L)); configureTokens();

        assertIdentity(service.loginWithPlatformAccount(81L, "buyer_1", "1234"));
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithPlatformAccount(82L, "buyer_1", "1234")).getCode());

        verify(memberUserMapper, times(2)).selectByLoginName("buyer_1");
        verify(userService, times(1)).isPasswordMatch("1234", "existing-hash");
        verify(oauth2TokenApi, times(1)).createAccessToken(any());
    }

    @Test void disabledAccountCannotObtainTokensEvenWithCorrectPassword() {
        configureAccount(member(9001L).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        assertEquals(AUTH_LOGIN_USER_DISABLED.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verify(loginLogApi).createLoginLog(argThat(log -> log.getResult().equals(LoginResultEnum.USER_DISABLED.getResult())));
        verifyNoInteractions(oauth2TokenApi);
    }

    @Test void foreignStoredTenantCannotIssueTokensOrRecordSuccessfulLogin() {
        configureAccount(member(9002L));
        assertEquals(AUTH_TENANT_MISMATCH.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verifyNoInteractions(oauth2TokenApi, loginLogApi);
        verify(userService, never()).updateUserLogin(anyLong(), anyString());
    }

    @Test void missingOrForeignReloadedTenantDoesNotTrustTheRequestHint() {
        configureAccount(member(null));
        when(userService.getUser(81L)).thenReturn(member(9002L));
        assertEquals(AUTH_TENANT_MISMATCH.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verifyNoInteractions(oauth2TokenApi, loginLogApi);
    }

    @Test void missingRequestTenantCannotBeInferredFromAnExistingAccount() {
        TenantContextHolder.clear(); configureAccount(member(9001L));
        assertEquals(AUTH_TENANT_MISMATCH.getCode(), assertThrows(ServiceException.class,
                () -> service.loginWithAccountPassword("buyer_1", "1234")).getCode());
        verifyNoInteractions(oauth2TokenApi, loginLogApi);
    }

    @Test void legacyRegisterAndAlreadyVerifiedSmsRegisterAreBothBlockedBeforeAccountWrites() {
        doThrow(exception(MallAccountPolicy.IMAGE_REQUIRED)).when(accountPolicy).rejectLegacyRegistration();
        AppAuthRegisterReqVO request = new AppAuthRegisterReqVO().setMobile("13900009991")
                .setPassword("Test1234").setNickname("legacy").setCode("123456").setAgree(true);
        assertEquals(MallAccountPolicy.IMAGE_REQUIRED.getCode(), assertThrows(ServiceException.class, () -> service.register(request)).getCode());
        assertEquals(MallAccountPolicy.IMAGE_REQUIRED.getCode(), assertThrows(ServiceException.class, () -> service.registerWithVerifiedMobile(request)).getCode());
        verifyNoInteractions(userService, memberUserMapper, smsCodeApi, customerAffiliations, platformAccounts, oauth2TokenApi);
    }

    @Test void smsLoginMayAuthenticateExistingPhoneButCannotAutomaticallyOpenAnAccount() {
        when(smsCodeApi.useSmsCode(any())).thenReturn(CommonResult.success(true));
        AppAuthSmsLoginReqVO request = new AppAuthSmsLoginReqVO().setMobile("13900009991").setCode("123456");
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class, () -> service.smsLogin(request)).getCode());
        verify(userService).getUserByMobile("13900009991"); verifyNoInteractions(oauth2TokenApi, customerAffiliations, socialUserApi);
        assertNoLegacyCreation();
    }

    @Test void existingSmsMemberStillAuthenticatesWithoutRecreatingItsIdentity() {
        when(smsCodeApi.useSmsCode(any())).thenReturn(CommonResult.success(true));
        when(userService.getUserByMobile("13900009991")).thenReturn(member(9001L).setMobile("13900009991"));
        configureTokens();
        assertIdentity(service.smsLogin(new AppAuthSmsLoginReqVO().setMobile("13900009991").setCode("123456")));
        assertNoLegacyCreation(); verifyNoInteractions(customerAffiliations);
    }

    @Test void unboundSocialIdentityCannotCreateOrBindANewMember() {
        AppAuthSocialLoginReqVO request = new AppAuthSocialLoginReqVO().setType(34).setCode("social-code").setState("state");
        when(socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), 34, "social-code", "state"))
                .thenReturn(CommonResult.success(new SocialUserRespDTO().setOpenid("openid").setNickname("social")));
        doThrow(exception(MallAccountPolicy.IMAGE_REQUIRED)).when(accountPolicy).rejectLegacyRegistration();
        assertEquals(MallAccountPolicy.IMAGE_REQUIRED.getCode(), assertThrows(ServiceException.class, () -> service.socialLogin(request)).getCode());
        verifyNoInteractions(userService, oauth2TokenApi, customerAffiliations);
        verify(socialUserApi, never()).bindSocialUser(any());
    }

    @Test void boundSocialIdentityKeepsExistingLoginCapability() {
        AppAuthSocialLoginReqVO request = new AppAuthSocialLoginReqVO().setType(34).setCode("social-code").setState("state");
        when(socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), 34, "social-code", "state"))
                .thenReturn(CommonResult.success(new SocialUserRespDTO().setOpenid("openid").setUserId(81L)));
        when(userService.getUser(81L)).thenReturn(member(9001L)); configureTokens();
        assertIdentity(service.socialLogin(request));
        verify(accountPolicy, never()).rejectLegacyRegistration(); assertNoLegacyCreation();
        verify(socialUserApi, never()).bindSocialUser(any());
    }

    @Test void wechatPhoneLoginCannotCreateAnAccountWhenPhoneIsNotRegistered() {
        when(socialClientApi.getWxMaPhoneNumberInfo(UserTypeEnum.MEMBER.getValue(), "phone-code"))
                .thenReturn(CommonResult.success(new SocialWxPhoneNumberInfoRespDTO().setPurePhoneNumber("13900009991")));
        AppAuthWeixinMiniAppLoginReqVO request = new AppAuthWeixinMiniAppLoginReqVO()
                .setPhoneCode("phone-code").setLoginCode("login-code").setState("state");
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), assertThrows(ServiceException.class, () -> service.weixinMiniAppLogin(request)).getCode());
        verify(userService).getUserByMobile("13900009991");
        assertNoLegacyCreation(); verifyNoInteractions(oauth2TokenApi, socialUserApi, customerAffiliations);
    }

    private void configureAccount(MemberUserDO member) {
        when(memberUserMapper.selectByLoginName("buyer_1")).thenReturn(member);
        when(userService.isPasswordMatch("1234", "existing-hash")).thenReturn(true);
    }
    private void stubRegistration(MemberUserDO member) {
        when(userService.registerAccountUser(any(), any(), any(), any(), any(), any(), any())).thenReturn(member);
    }
    private void configureTokens() {
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(CommonResult.success(new OAuth2AccessTokenRespDTO()
                .setUserId(81L).setUserType(UserTypeEnum.MEMBER.getValue()).setAccessToken("access").setRefreshToken("refresh")));
    }
    private void assertNoLegacyCreation() {
        verify(userService, never()).createUserIfAbsent(any(), any(), any());
        verify(userService, never()).createUser(any(), any(), any(), any());
        verify(userService, never()).registerUser(any(), any(), any(), any(), any());
    }
    private static void assertIdentity(AppAuthLoginRespVO result) {
        assertEquals(9001L, result.getTenantId()); assertEquals(81L, result.getUserId());
        assertEquals("access", result.getAccessToken()); assertEquals("refresh", result.getRefreshToken());
    }
    private static MemberUserDO member(Long tenant) {
        MemberUserDO member = new MemberUserDO().setId(81L).setLoginName("buyer_1").setPassword("existing-hash")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        member.setTenantId(tenant); return member;
    }
    private static AppMallImageAuthReqVO.Register registration() {
        AppMallImageAuthReqVO.Register body = new AppMallImageAuthReqVO.Register();
        body.setTenantId(9001L); body.setLoginName("buyer_1"); body.setPassword("Test1234"); body.setConfirmPassword("Test1234");
        body.setNickname("图片会员"); body.setContactMobile("13900009991"); body.setMemberIdentity("supplier"); body.setAgree(true);
        body.setCaptchaSession("b".repeat(32)); body.setImageChallengeId("a".repeat(32)); body.setImageCode("AB2CD"); return body;
    }
}
