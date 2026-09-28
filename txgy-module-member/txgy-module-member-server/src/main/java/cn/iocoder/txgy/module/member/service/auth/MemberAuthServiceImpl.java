package cn.iocoder.txgy.module.member.service.auth;

import cn.hutool.core.lang.Assert;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.TerminalEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.util.monitor.TracerUtils;
import cn.iocoder.txgy.framework.common.util.servlet.ServletUtils;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.*;
import cn.iocoder.txgy.module.member.convert.auth.AuthConvert;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
import cn.iocoder.txgy.module.system.api.logger.LoginLogApi;
import cn.iocoder.txgy.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.SocialUserApi;
import cn.iocoder.txgy.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.txgy.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.txgy.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.txgy.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.txgy.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.txgy.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.txgy.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.txgy.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils.getTerminal;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

/**
 * 会员的认证 Service 接口
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class MemberAuthServiceImpl implements MemberAuthService {

    @Resource
    private MemberUserService userService;
    @Resource
    private cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper memberUserMapper;
    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private LoginLogApi loginLogApi;
    @Resource
    private SocialUserApi socialUserApi;
    @Resource
    private SocialClientApi socialClientApi;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Resource
    private cn.iocoder.txgy.module.member.service.tenant.MallCustomerAffiliationService customerAffiliations;
    @Resource
    private cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy accountPolicy;
    @Resource
    private cn.iocoder.txgy.module.member.service.tenant.MallImageChallengeService imageChallenges;
    @Resource
    private cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService platformAccounts;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAuthLoginRespVO registerWithVerifiedImage(cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO.Register body) {
        if (!accountPolicy.isEnabled()) throw exception(MALL_TENANT_UNAVAILABLE);
        MemberUserDO user = userService.registerAccountUser(body.getLoginName(), body.getPassword(), body.getNickname(),
                body.getContactMobile(), body.getMemberIdentity(), getClientIP(), getTerminal());
        customerAffiliations.bindRegistration(user.getId());
        platformAccounts.createForRegistration(user, TenantContextHolder.getRequiredTenantId());
        return createTokenAfterLoginSuccess(user, user.getLoginName(), LoginLogTypeEnum.LOGIN_MOBILE, null);
    }

    @Override
    public AppAuthLoginRespVO loginWithAccountPassword(String account, String password) {
        if (!accountPolicy.isEnabled()) throw exception(MALL_TENANT_UNAVAILABLE);
        String normalized = account.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches("1[3-9][0-9]{9}")) {
            return createTokenAfterLoginSuccess(login0(normalized, password), normalized, LoginLogTypeEnum.LOGIN_MOBILE, null);
        }
        normalized = cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy.normalize(normalized);
        MemberUserDO user = memberUserMapper.selectByLoginName(normalized);
        if (user == null || !userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user == null ? null : user.getId(), normalized, LoginLogTypeEnum.LOGIN_MOBILE, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        return createTokenAfterLoginSuccess(user, normalized, LoginLogTypeEnum.LOGIN_MOBILE, null);
    }

    @Override
    public AppAuthLoginRespVO loginWithPlatformAccount(Long expectedMemberId, String account, String password) {
        if (expectedMemberId == null || expectedMemberId <= 0 || account == null) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        String normalized = account.trim().toLowerCase(java.util.Locale.ROOT);
        MemberUserDO user;
        if (normalized.matches("1[3-9][0-9]{9}")) {
            user = userService.getUserByMobile(normalized);
        } else {
            if (!normalized.matches("[a-z][a-z0-9_]{5,19}")) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            user = memberUserMapper.selectByLoginName(normalized);
        }
        if (user == null || !Objects.equals(user.getId(), expectedMemberId)
                || !userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user != null && Objects.equals(user.getId(), expectedMemberId) ? user.getId() : null,
                    normalized, LoginLogTypeEnum.LOGIN_MOBILE, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        return createTokenAfterLoginSuccess(user, normalized, LoginLogTypeEnum.LOGIN_MOBILE, null);
    }

    @Override
    public AppAuthLoginRespVO login(AppAuthLoginReqVO reqVO) {
        if (accountPolicy.isEnabled()) {
            var request = ServletUtils.getRequest();
            imageChallenges.limitPasswordAttempts(reqVO.getMobile(), request == null ? "internal" : request.getRemoteAddr());
        }
        // 使用手机 + 密码，进行登录。
        MemberUserDO user = login0(reqVO.getMobile(), reqVO.getPassword());

        // 如果 socialType 非空，说明需要绑定社交用户
        String openid = null;
        if (reqVO.getSocialType() != null) {
            openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())).getCheckedData();
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE, openid);
    }

    @Override
    @Transactional
    public AppAuthLoginRespVO register(AppAuthRegisterReqVO reqVO) {
        accountPolicy.rejectLegacyRegistration();
        String clientIp = getClientIP();
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                .setMobile(reqVO.getMobile())
                .setCode(reqVO.getCode())
                .setScene(SmsSceneEnum.MEMBER_LOGIN.getScene())
                .setUsedIp(clientIp)).checkError();
        return registerWithVerifiedMobile(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAuthLoginRespVO registerWithVerifiedMobile(AppAuthRegisterReqVO reqVO) {
        accountPolicy.rejectLegacyRegistration();
        String clientIp = getClientIP();
        MemberUserDO user = userService.registerUser(reqVO.getMobile(), reqVO.getPassword(),
                reqVO.getNickname(), clientIp, getTerminal());
        if (reqVO.getMemberIdentity() != null) {
            user.setMemberIdentity(reqVO.getMemberIdentity());
            memberUserMapper.updateById(new MemberUserDO().setId(user.getId())
                    .setMemberIdentity(reqVO.getMemberIdentity()));
        }
        customerAffiliations.bindRegistration(user.getId());
        platformAccounts.createForRegistration(user, TenantContextHolder.getRequiredTenantId());
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE, null);
    }

    @Override
    public AppAuthLoginRespVO loginWithVerifiedMobile(String mobile) {
        MemberUserDO user = userService.getUserByMobile(mobile);
        if (user == null) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        return createTokenAfterLoginSuccess(user, mobile, LoginLogTypeEnum.LOGIN_SMS, null);
    }

    @Override
    @Transactional
    public AppAuthLoginRespVO smsLogin(AppAuthSmsLoginReqVO reqVO) {
        // 校验验证码
        String userIp = getClientIP();
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO, SmsSceneEnum.MEMBER_LOGIN.getScene(), userIp)).checkError();

        // 获得获得注册用户
        MemberUserDO user = accountPolicy.isEnabled() ? userService.getUserByMobile(reqVO.getMobile())
                : userService.createUserIfAbsent(reqVO.getMobile(), userIp, getTerminal());
        if (user == null) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        Assert.notNull(user, "获取用户失败，结果为空");

        // 校验是否禁用
        validateUserStatus(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_SMS);

        // 如果 socialType 非空，说明需要绑定社交用户
        String openid = null;
        if (reqVO.getSocialType() != null) {
            openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())).getCheckedData();
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_SMS, openid);
    }

    @Override
    @Transactional
    public AppAuthLoginRespVO socialLogin(AppAuthSocialLoginReqVO reqVO) {
        // 使用 code 授权码，进行登录。然后，获得到绑定的用户编号
        SocialUserRespDTO socialUser = socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), reqVO.getType(),
                reqVO.getCode(), reqVO.getState()).getCheckedData();
        if (socialUser == null) {
            throw exception(AUTH_SOCIAL_USER_NOT_FOUND);
        }

        // 情况一：已绑定，直接读取用户信息
        MemberUserDO user;
        if (socialUser.getUserId() != null) {
            user = userService.getUser(socialUser.getUserId());
        // 情况二：未绑定，注册用户 + 绑定用户
        } else {
            accountPolicy.rejectLegacyRegistration();
            user = userService.createUser(socialUser.getNickname(), socialUser.getAvatar(), getClientIP(), getTerminal());
            socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getType(), reqVO.getCode(), reqVO.getState())).checkError();
        }
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 校验是否禁用
        validateUserStatus(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL);

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL, socialUser.getOpenid());
    }

    @Override
    public AppAuthLoginRespVO weixinMiniAppLogin(AppAuthWeixinMiniAppLoginReqVO reqVO) {
        // 获得对应的手机号信息
        SocialWxPhoneNumberInfoRespDTO phoneNumberInfo = socialClientApi.getWxMaPhoneNumberInfo(
                UserTypeEnum.MEMBER.getValue(), reqVO.getPhoneCode()).getCheckedData();
        Assert.notNull(phoneNumberInfo, "获得手机信息失败，结果为空");

        // 获得获得注册用户
        MemberUserDO user = accountPolicy.isEnabled() ? userService.getUserByMobile(phoneNumberInfo.getPurePhoneNumber())
                : userService.createUserIfAbsent(phoneNumberInfo.getPurePhoneNumber(),
                    getClientIP(), TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal());
        if (user == null) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        Assert.notNull(user, "获取用户失败，结果为空");
        validateUserStatus(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL);

        // 绑定社交用户
        String openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                SocialTypeEnum.WECHAT_MINI_PROGRAM.getType(), reqVO.getLoginCode(), reqVO.getState())).getCheckedData();

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL, openid);
    }

    private AppAuthLoginRespVO createTokenAfterLoginSuccess(MemberUserDO user, String mobile,
                                                            LoginLogTypeEnum logType, String openid) {
        // INSERT 的租户拦截器只改写 SQL，不回填新建用户对象，返回前读取实际账号归属。
        if (user.getTenantId() == null) {
            MemberUserDO persistedUser = userService.getUser(user.getId());
            if (persistedUser == null || persistedUser.getTenantId() == null) {
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }
            user.setTenantId(persistedUser.getTenantId());
        }
        if (!Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId())) {
            throw exception(AUTH_TENANT_MISMATCH);
        }
        // 统一校验用户状态，避免登录方式增加后遗漏
        validateUserStatus(user, mobile, logType);

        // 插入登陆日志
        createLoginLog(user.getId(), mobile, logType, LoginResultEnum.SUCCESS);
        // 创建 Token 令牌
        OAuth2AccessTokenRespDTO accessTokenRespDTO = oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(user.getId()).setUserType(getUserType().getValue())
                .setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT)).getCheckedData();
        // 构建返回结果
        return AuthConvert.INSTANCE.convert(accessTokenRespDTO, openid).setTenantId(user.getTenantId());
    }

    private void validateUserStatus(MemberUserDO user, String mobile, LoginLogTypeEnum logType) {
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), mobile, logType, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

    @Override
    public String getSocialAuthorizeUrl(Integer type, String redirectUri) {
        return socialClientApi.getAuthorizeUrl(type, UserTypeEnum.MEMBER.getValue(), redirectUri).getCheckedData();
    }

    private MemberUserDO login0(String mobile, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_MOBILE;
        // 校验账号是否存在
        MemberUserDO user = userService.getUserByMobile(mobile);
        if (user == null) {
            createLoginLog(null, mobile, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), mobile, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, mobile, logTypeEnum);
        return user;
    }

    private void createLoginLog(Long userId, String mobile, LoginLogTypeEnum logType, LoginResultEnum loginResult) {
        // 插入登录日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(mobile);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogApi.createLoginLog(reqDTO).checkError();
        // 更新最后登录时间
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(userId, getClientIP());
        }
    }

    @Override
    public void logout(String token) {
        // 删除访问令牌
        OAuth2AccessTokenRespDTO accessTokenRespDTO = oauth2TokenApi.removeAccessToken(token).getCheckedData();
        if (accessTokenRespDTO == null) {
            return;
        }
        // 删除成功，则记录登出日志
        createLogoutLog(accessTokenRespDTO.getUserId());
    }

    @Override
    public void sendSmsCode(Long userId, AppAuthSmsSendReqVO reqVO) {
        // 情况 1：如果是修改手机场景，需要校验新手机号是否已经注册，说明不能使用该手机了
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene())) {
            MemberUserDO user = userService.getUserByMobile(reqVO.getMobile());
            if (user != null && !Objects.equals(user.getId(), userId)) {
                throw exception(AUTH_MOBILE_USED);
            }
        }
        // 情况 2：如果是重置密码场景，需要校验手机号是存在的
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_RESET_PASSWORD.getScene())) {
            MemberUserDO user = userService.getUserByMobile(reqVO.getMobile());
            if (user == null) {
                throw exception(USER_MOBILE_NOT_EXISTS);
            }
        }
        // 情况 3：如果是修改密码场景，需要查询手机号，无需前端传递
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_UPDATE_PASSWORD.getScene())) {
            MemberUserDO user = userService.getUser(userId);
            // TODO 芋艿：后续 member user 手机非强绑定，这块需要做下调整；
            reqVO.setMobile(user.getMobile());
        }

        // 执行发送
        smsCodeApi.sendSmsCode(AuthConvert.INSTANCE.convert(reqVO).setCreateIp(getClientIP())).checkError();
    }

    @Override
    public void validateSmsCode(Long userId, AppAuthSmsValidateReqVO reqVO) {
        smsCodeApi.validateSmsCode(AuthConvert.INSTANCE.convert(reqVO)).checkError();
    }

    @Override
    public AppAuthLoginRespVO refreshToken(String refreshToken) {
        // 现有 check 接口支持刷新令牌；必须在会撤销旧访问令牌的 refresh RPC 前校验。
        OAuth2AccessTokenCheckRespDTO checked = oauth2TokenApi.checkAccessToken(refreshToken).getCheckedData();
        if (checked == null || checked.getUserId() == null || checked.getTenantId() == null
                || !Objects.equals(checked.getUserType(), UserTypeEnum.MEMBER.getValue())
                || !Objects.equals(checked.getTenantId(), TenantContextHolder.getTenantId())) {
            throw exception(AUTH_TENANT_MISMATCH);
        }
        MemberUserDO user = TenantUtils.execute(checked.getTenantId(), () -> userService.getUser(checked.getUserId()));
        if (user == null || !Objects.equals(user.getId(), checked.getUserId())
                || !Objects.equals(user.getTenantId(), checked.getTenantId())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!CommonStatusEnum.isEnable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        OAuth2AccessTokenRespDTO accessTokenDO = TenantUtils.execute(checked.getTenantId(),
                () -> oauth2TokenApi.refreshAccessToken(refreshToken,
                        OAuth2ClientConstants.CLIENT_ID_DEFAULT).getCheckedData());
        return AuthConvert.INSTANCE.convert(accessTokenDO, null).setTenantId(checked.getTenantId());
    }

    private void createLogoutLog(Long userId) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(LoginLogTypeEnum.LOGOUT_SELF.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(getMobile(userId));
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogApi.createLoginLog(reqDTO).checkError();
    }

    private String getMobile(Long userId) {
        if (userId == null) {
            return null;
        }
        MemberUserDO user = userService.getUser(userId);
        return user == null ? null : user.getLoginName() != null ? user.getLoginName() : user.getMobile();
    }

    private UserTypeEnum getUserType() {
        return UserTypeEnum.MEMBER;
    }

}
