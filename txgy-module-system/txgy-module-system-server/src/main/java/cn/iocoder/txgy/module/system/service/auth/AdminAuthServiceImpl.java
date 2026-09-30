package cn.iocoder.txgy.module.system.service.auth;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.util.monitor.TracerUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.util.servlet.ServletUtils;
import cn.iocoder.txgy.framework.common.util.validation.ValidationUtils;
import cn.iocoder.txgy.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.txgy.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.txgy.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.txgy.module.system.controller.admin.auth.vo.*;
import cn.iocoder.txgy.module.system.convert.auth.AuthConvert;
import cn.iocoder.txgy.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.txgy.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.txgy.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.txgy.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.txgy.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.txgy.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.txgy.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.txgy.module.system.service.logger.LoginLogService;
import cn.iocoder.txgy.module.system.service.member.MemberService;
import cn.iocoder.txgy.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.txgy.module.system.service.social.SocialUserService;
import cn.iocoder.txgy.module.system.service.tenant.TenantService;
import cn.iocoder.txgy.module.system.service.user.AdminUserService;
import cn.iocoder.txgy.module.hrm.api.employee.HrmEmployeeApi;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.txgy.module.system.enums.ErrorCodeConstants.*;

/**
 * Auth Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 无租户个人用户使用的保留租户编号。 */
    private static final Long PERSONAL_TENANT_ID = 0L;

    @Resource
    private AdminUserService userService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private SocialUserService socialUserService;
    @Resource
    private MemberService memberService;
    @Resource
    private Validator validator;
    @Resource
    private CaptchaService captchaService;
    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private TenantFrameworkService tenantFrameworkService;
    @Resource
    private TenantService tenantService;
    @Resource
    private HrmEmployeeApi hrmEmployeeApi;

    /**
     * 验证码的开关，默认为 true
     */
    @Value("${txgy.captcha.enable:true}")
    @Setter // 为了单测：开启或者关闭验证码
    private Boolean captchaEnable;

    @Override
    public AdminUserDO authenticate(String username, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_USERNAME;
        // 校验账号是否存在
        AdminUserDO user = userService.getUserByUsername(username);
        // 用户名不存在时，尝试按「工号」登录：工号 → 员工档案 → 绑定的后台用户
        if (user == null) {
            Long jobNumberUserId = getUserIdByJobNumber(username);
            if (jobNumberUserId != null) {
                user = userService.getUser(jobNumberUserId);
            }
        }
        if (user == null) {
            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, username, logTypeEnum);
        return user;
    }

    /**
     * 按工号解析员工绑定的后台用户编号
     *
     * <p>工号登录是增强能力：HRM 模块不可用时静默降级为按用户名登录，不影响原有登录。</p>
     */
    private Long getUserIdByJobNumber(String jobNumber) {
        if (StrUtil.isBlank(jobNumber)) {
            return null;
        }
        try {
            return hrmEmployeeApi.getUserIdByJobNumber(jobNumber).getCheckedData();
        } catch (Throwable ex) {
            return null;
        }
    }

    @Override
    @DataPermission(enable = false)
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        // 校验验证码
        validateCaptcha(reqVO);

        // 个人账号固定在保留租户 0 内认证，避免在所有企业租户中按账号匹配。
        if (reqVO.isPersonalLogin()) {
            final AuthLoginRespVO[] result = new AuthLoginRespVO[1];
            TenantUtils.execute(PERSONAL_TENANT_ID, () -> result[0] = doLogin(reqVO));
            return result[0];
        }

        // 旧客户端未传 loginType 时按企业登录处理，企业登录必须有明确的租户上下文。
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception0(BAD_REQUEST.getCode(), "企业登录需要传递租户编号");
        }
        // 认证 URL 对缺少租户的请求已放行，这里补回租户有效性校验，避免禁用/过期租户绕过过滤器。
        tenantFrameworkService.validTenant(tenantId);

        // 登录 Controller 使用了 @TenantIgnore，以便个人账号和企业账号共用入口。
        // 企业认证时必须显式恢复所选租户上下文，否则用户名查询会跨租户命中同名账号。
        final AuthLoginRespVO[] result = new AuthLoginRespVO[1];
        TenantUtils.execute(tenantId, () -> result[0] = doLogin(reqVO));
        return result[0];
    }

    private AuthLoginRespVO doLogin(AuthLoginReqVO reqVO) {

        // 使用账号密码，进行登录
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        // 企业登录：账号所属租户必须与登录时选择的租户一致，否则直接拒绝。
        // 背景：认证接口在免租户白名单内（允许不带租户编号），若不校验，会出现
        // 「登录时选了租户 A、账号实际属于租户 B」的会话，进入系统后所有带权限校验的
        // 接口都会返回 403「没有该操作权限」，而业务数据却仍能看到，极难排查。
        Long loginTenantId = TenantContextHolder.getTenantId();
        if (loginTenantId != null && user.getTenantId() != null
                && !user.getTenantId().equals(loginTenantId)) {
            String tenantName = "";
            try {
                TenantDO tenant = tenantService.getTenant(user.getTenantId());
                tenantName = tenant != null ? tenant.getName() : "";
            } catch (Throwable ignored) {
                // 查询租户名称失败不影响拦截
            }
            throw exception0(BAD_REQUEST.getCode(), StrUtil.format(
                    "该账号不属于所选租户，请重新选择租户后登录（账号所属租户：{}）",
                    StrUtil.blankToDefault(tenantName, "租户 " + user.getTenantId())));
        }

        // 如果 socialType 非空，说明需要绑定社交用户
        if (reqVO.getSocialType() != null) {
            socialUserService.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState()));
        }
        // 会话统一绑定到「账号实际所属租户」：认证接口对缺少租户的请求放行，
        // 若登录时选错租户（例如选了平台租户 1、账号却属于租户 2020），
        // 直接按请求租户签发令牌会导致会话租户与账号租户不一致，后续所有请求都会被判越权。
        if (user.getTenantId() != null && !user.getTenantId().equals(TenantContextHolder.getTenantId())) {
            final AuthLoginRespVO[] result = new AuthLoginRespVO[1];
            TenantUtils.execute(user.getTenantId(), () -> result[0] = createTokenAfterLoginSuccess(
                    user, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME));
            return result[0];
        }
        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    @Override
    public void sendSmsCode(AuthSmsSendReqVO reqVO) {
        // 如果是重置密码场景，需要校验图形验证码是否正确
        if (Objects.equals(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene(), reqVO.getScene())) {
            ResponseModel response = doValidateCaptcha(reqVO);
            if (!response.isSuccess()) {
                throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
            }
        }

        // 登录场景，验证是否存在
        if (userService.getUserByMobile(reqVO.getMobile()) == null) {
            throw exception(AUTH_MOBILE_NOT_EXISTS);
        }
        // 发送验证码
        smsCodeApi.sendSmsCode(AuthConvert.INSTANCE.convert(reqVO).setCreateIp(getClientIP())).checkError();
    }

    @Override
    public AuthLoginRespVO smsLogin(AuthSmsLoginReqVO reqVO) {
        // 校验验证码
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO, SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(), getClientIP()))
                .checkError();

        // 获得用户信息
        AdminUserDO user = userService.getUserByMobile(reqVO.getMobile());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE);
    }

    private void createLoginLog(Long userId, String username,
                                LoginLogTypeEnum logTypeEnum, LoginResultEnum loginResult) {
        // 插入登录日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogService.createLoginLog(reqDTO);
        // 更新最后登录时间
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(userId, ServletUtils.getClientIP());
        }
    }

    @Override
    public AuthLoginRespVO socialLogin(AuthSocialLoginReqVO reqVO) {
        // 使用 code 授权码，进行登录。然后，获得到绑定的用户编号
        SocialUserRespDTO socialUser = socialUserService.getSocialUserByCode(UserTypeEnum.ADMIN.getValue(), reqVO.getType(),
                reqVO.getCode(), reqVO.getState());
        if (socialUser == null || socialUser.getUserId() == null) {
            throw exception(AUTH_THIRD_LOGIN_NOT_BIND);
        }

        // 获得用户
        AdminUserDO user = userService.getUser(socialUser.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getUsername(), LoginLogTypeEnum.LOGIN_SOCIAL);
    }

    @VisibleForTesting
    void validateCaptcha(AuthLoginReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        // 校验验证码
        if (!response.isSuccess()) {
            // 创建登录失败日志（验证码不正确)
            createLoginLog(null, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME, LoginResultEnum.CAPTCHA_CODE_ERROR);
            throw exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    private ResponseModel doValidateCaptcha(CaptchaVerificationReqVO reqVO) {
        // 如果验证码关闭，则不进行校验
        if (!captchaEnable) {
            return ResponseModel.success();
        }
        ValidationUtils.validate(validator, reqVO, CaptchaVerificationReqVO.CodeEnableGroup.class);
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaVerification(reqVO.getCaptchaVerification());
        return captchaService.verification(captchaVO);
    }

    private AuthLoginRespVO createTokenAfterLoginSuccess(AdminUserDO user, String username, LoginLogTypeEnum logType) {
        // 统一校验用户状态，避免短信、社交等登录方式遗漏
        validateUserStatus(user, username, logType);

        // 插入登陆日志
        createLoginLog(user.getId(), username, logType, LoginResultEnum.SUCCESS);
        // 创建访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(), getUserType().getValue(),
                OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        // 构建返回结果，并回传账号实际所属租户，供前端纠正登录租户
        // 背景：认证接口对缺少租户的请求放行，可能出现"登录租户与账号所属租户不一致"的会话错配
        AuthLoginRespVO respVO = BeanUtils.toBean(accessTokenDO, AuthLoginRespVO.class);
        respVO.setTenantId(user.getTenantId());
        return respVO;
    }

    private void validateUserStatus(AdminUserDO user, String username, LoginLogTypeEnum logType) {
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), username, logType, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT);
        return BeanUtils.toBean(accessTokenDO, AuthLoginRespVO.class);
    }

    @Override
    public void logout(String token, Integer logType) {
        // 删除访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.removeAccessToken(token);
        if (accessTokenDO == null) {
            return;
        }
        // 删除成功，则记录登出日志
        createLogoutLog(accessTokenDO.getUserId(), accessTokenDO.getUserType(), logType);
    }

    private void createLogoutLog(Long userId, Integer userType, Integer logType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType);
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(userType);
        if (ObjectUtil.equal(getUserType().getValue(), userType)) {
            reqDTO.setUsername(getUsername(userId));
        } else {
            reqDTO.setUsername(memberService.getMemberUserMobile(userId));
        }
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

    private String getUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserDO user = userService.getUser(userId);
        return user != null ? user.getUsername() : null;
    }

    private UserTypeEnum getUserType() {
        return UserTypeEnum.ADMIN;
    }

    @Override
    public AuthLoginRespVO register(AuthRegisterReqVO registerReqVO) {
        // 1. 校验验证码
        validateCaptcha(registerReqVO);

        // 2. 个人账号固定注册在保留租户 0，与个人密码登录保持同一数据边界。
        if (registerReqVO.isPersonalRegister()) {
            final AuthLoginRespVO[] result = new AuthLoginRespVO[1];
            TenantUtils.execute(PERSONAL_TENANT_ID, () -> {
                AdminUserDO user = userService.registerUser(registerReqVO);
                result[0] = createTokenAfterLoginSuccess(user, registerReqVO.getUsername(),
                        LoginLogTypeEnum.LOGIN_USERNAME);
            });
            return result[0];
        }

        // 3. 企业账号沿用当前租户注册流程。注册 URL 为支持个人注册而忽略租户过滤，
        //    因此这里补回企业租户的存在性校验，避免缺少或失效租户时写入数据。
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception0(BAD_REQUEST.getCode(), "企业注册需要传递租户编号");
        }
        tenantFrameworkService.validTenant(tenantId);

        AdminUserDO user = userService.registerUser(registerReqVO);

        // 4. 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, registerReqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    @VisibleForTesting
    void validateCaptcha(AuthRegisterReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        // 验证不通过
        if (!response.isSuccess()) {
            throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AuthResetPasswordReqVO reqVO) {
        AdminUserDO userByMobile = userService.getUserByMobile(reqVO.getMobile());
        if (userByMobile == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }

        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                .setCode(reqVO.getCode())
                .setMobile(reqVO.getMobile())
                .setScene(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene())
                .setUsedIp(getClientIP())
        ).checkError();

        userService.updateUserPassword(userByMobile.getId(), reqVO.getPassword());
    }
}
