package cn.iocoder.txgy.module.member.service.auth;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
import cn.iocoder.txgy.module.system.api.logger.LoginLogApi;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.enums.oauth2.OAuth2ClientConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberAuthTenantTest {

    private MemberAuthServiceImpl service;
    private MemberUserService userService;
    private OAuth2TokenCommonApi tokenApi;

    @BeforeEach
    void setUp() {
        service = new MemberAuthServiceImpl();
        ReflectionTestUtils.setField(service, "accountPolicy", mock(cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy.class));
        ReflectionTestUtils.setField(service, "customerAffiliations", mock(cn.iocoder.txgy.module.member.service.tenant.MallCustomerAffiliationService.class));
        userService = mock(MemberUserService.class);
        tokenApi = mock(OAuth2TokenCommonApi.class);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "oauth2TokenApi", tokenApi);
        TenantContextHolder.setTenantId(9001L);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void refreshWithWrongTenantCannotRevokeOrIssueAnyToken() {
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(checkedToken(9002L)));
        assertRefreshRejected(AUTH_TENANT_MISMATCH.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void adminRefreshTokenCannotPassThroughMemberEndpoint() {
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(
                checkedToken(9001L).setUserType(UserTypeEnum.ADMIN.getValue())));
        assertRefreshRejected(AUTH_TENANT_MISMATCH.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void missingRequestTenantCannotBeReplacedByUnverifiedClientState() {
        TenantContextHolder.clear();
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(checkedToken(9001L)));
        assertRefreshRejected(AUTH_TENANT_MISMATCH.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void deletedOrWrongTenantMemberCannotRefresh() {
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(checkedToken(9001L)));
        assertRefreshRejected(AUTH_LOGIN_BAD_CREDENTIALS.getCode());
        when(userService.getUser(81L)).thenReturn(member(9002L));
        assertRefreshRejected(AUTH_LOGIN_BAD_CREDENTIALS.getCode());
    }

    @Test
    void disabledMemberCannotRefreshEvenWhenTokenRemainsValid() {
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(checkedToken(9001L)));
        when(userService.getUser(81L)).thenReturn(member(9001L).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        assertRefreshRejected(AUTH_LOGIN_USER_DISABLED.getCode());
    }

    @Test
    void validSameTenantRefreshReturnsTrustedTenantAndRestoresContext() {
        when(tokenApi.checkAccessToken("refresh")).thenReturn(CommonResult.success(checkedToken(9001L)));
        TenantContextHolder.setIgnore(true);
        when(userService.getUser(81L)).thenAnswer(invocation -> {
            assertEquals(9001L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return member(9001L);
        });
        when(tokenApi.refreshAccessToken("refresh", OAuth2ClientConstants.CLIENT_ID_DEFAULT)).thenAnswer(invocation -> {
            assertEquals(9001L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return CommonResult.success(issuedToken());
        });

        AppAuthLoginRespVO result = service.refreshToken("refresh");

        assertEquals(9001L, result.getTenantId());
        assertEquals(81L, result.getUserId());
        assertEquals("access", result.getAccessToken());
        assertTrue(TenantContextHolder.isIgnore());
        assertEquals(9001L, TenantContextHolder.getTenantId());
        verify(tokenApi).refreshAccessToken("refresh", OAuth2ClientConstants.CLIENT_ID_DEFAULT);
    }

    @Test
    void ordinaryLoginReturnsTheStoredAccountTenant() {
        configurePasswordLogin(member(9001L));
        AppAuthLoginRespVO result = service.login(loginRequest());
        assertEquals(9001L, result.getTenantId());
        verify(userService, never()).getUser(anyLong());
    }

    @Test
    void loginCannotIssueTokenForMemberFromDifferentTenant() {
        configurePasswordLogin(member(9002L));
        assertLoginTenantMismatch();
    }

    @Test
    void reloadedMemberTenantMustAlsoMatchTheAuthenticationContext() {
        configurePasswordLogin(member(null));
        when(userService.getUser(81L)).thenReturn(member(9002L));
        assertLoginTenantMismatch();
    }

    @Test
    void newlyCreatedUserObjectWithoutFilledTenantIsReloadedBeforeIssuingToken() {
        configureTokenIssue();
        SmsCodeApi smsCodeApi = mock(SmsCodeApi.class);
        ReflectionTestUtils.setField(service, "smsCodeApi", smsCodeApi);
        when(smsCodeApi.useSmsCode(any())).thenReturn(CommonResult.success(true));
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(member(null));
        when(userService.getUser(81L)).thenReturn(member(9001L));
        AppAuthLoginRespVO result = service.register(new AppAuthRegisterReqVO().setMobile("test-mobile")
                .setPassword("test-password").setNickname("test-member").setCode("123456").setAgree(true));
        assertEquals(9001L, result.getTenantId());
        verify(userService).getUser(81L);
    }

    private void configurePasswordLogin(MemberUserDO user) {
        configureTokenIssue();
        when(userService.getUserByMobile("test-mobile")).thenReturn(user);
        when(userService.isPasswordMatch("test-password", "test-hash")).thenReturn(true);
    }

    private void configureTokenIssue() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        LoginLogApi logApi = mock(LoginLogApi.class);
        ReflectionTestUtils.setField(service, "loginLogApi", logApi);
        when(logApi.createLoginLog(any())).thenReturn(CommonResult.success(true));
        when(tokenApi.createAccessToken(any())).thenReturn(CommonResult.success(issuedToken()));
    }

    private AppAuthLoginReqVO loginRequest() {
        return new AppAuthLoginReqVO().setMobile("test-mobile").setPassword("test-password");
    }

    private void assertLoginTenantMismatch() {
        assertEquals(AUTH_TENANT_MISMATCH.getCode(),
                assertThrows(ServiceException.class, () -> service.login(loginRequest())).getCode());
        verify(tokenApi, never()).createAccessToken(any());
        verify(userService, never()).updateUserLogin(anyLong(), anyString());
        LoginLogApi logApi = (LoginLogApi) ReflectionTestUtils.getField(service, "loginLogApi");
        verifyNoInteractions(logApi);
    }

    private void assertRefreshRejected(Integer errorCode) {
        assertEquals(errorCode, assertThrows(ServiceException.class, () -> service.refreshToken("refresh")).getCode());
        verify(tokenApi, never()).refreshAccessToken(anyString(), anyString());
        verify(tokenApi, never()).createAccessToken(any());
        verify(tokenApi, never()).removeAccessToken(anyString());
    }

    private OAuth2AccessTokenCheckRespDTO checkedToken(Long tenantId) {
        return new OAuth2AccessTokenCheckRespDTO().setUserId(81L)
                .setUserType(UserTypeEnum.MEMBER.getValue()).setTenantId(tenantId);
    }

    private MemberUserDO member(Long tenantId) {
        MemberUserDO user = new MemberUserDO().setId(81L).setPassword("test-hash")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setTenantId(tenantId);
        return user;
    }

    private OAuth2AccessTokenRespDTO issuedToken() {
        return new OAuth2AccessTokenRespDTO().setUserId(81L).setUserType(UserTypeEnum.MEMBER.getValue())
                .setAccessToken("access").setRefreshToken("refresh");
    }

}
