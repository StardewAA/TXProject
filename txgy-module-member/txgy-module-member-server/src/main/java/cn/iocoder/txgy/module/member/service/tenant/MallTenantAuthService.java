package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginReqVO;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.Purpose.*;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class MallTenantAuthService {
    private final MallAuthPolicy policy;
    private final MallSmsChallengeService challenges;
    private final MemberAuthService auth;
    private final MemberUserMapper users;
    private final PasswordEncoder passwords;
    private final OAuth2TokenCommonApi tokens;

    public AppAuthLoginRespVO passwordLogin(AppMallAuthReqVO.PasswordLogin request, String peer) {
        long tenant = policy.resolve(request.getTenantId(), false);
        challenges.limitPasswordAttempts(request.getMobile(), peer);
        // Build an allowlisted VO: no implicit social binding or tenant/header mass assignment.
        return inTenant(tenant, () -> auth.login(new AppAuthLoginReqVO()
                .setMobile(request.getMobile()).setPassword(request.getPassword())));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppAuthLoginRespVO register(AppMallAuthReqVO.Register request, String peer) {
        long tenant = challenges.consume(request.getTenantId(), request.getMobile(), REGISTER,
                request.getChallengeId(), request.getCode(), peer);
        try {
            return inTenant(tenant, () -> auth.registerWithVerifiedMobile(new AppAuthRegisterReqVO()
                    .setMobile(request.getMobile()).setPassword(request.getPassword()).setNickname(request.getNickname())
                    .setMemberIdentity(request.getMemberIdentity()).setAgree(request.getAgree())));
        } catch (DuplicateKeyException error) {
            // Includes concurrent registration and an existing soft-deleted mobile+tenant unique key.
            throw exception(AUTH_MOBILE_USED);
        }
    }

    public AppAuthLoginRespVO smsLogin(AppMallAuthReqVO.Challenge request, String peer) {
        long tenant = challenges.consume(request.getTenantId(), request.getMobile(), SMS_LOGIN,
                request.getChallengeId(), request.getCode(), peer);
        return inTenant(tenant, () -> auth.loginWithVerifiedMobile(request.getMobile()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AppMallAuthReqVO.ResetPassword request, String peer) {
        long tenant = challenges.consume(request.getTenantId(), request.getMobile(), RESET_PASSWORD,
                request.getChallengeId(), request.getCode(), peer);
        TenantUtils.execute(tenant, () -> {
            MemberUserDO user = users.selectByMobile(request.getMobile());
            if (user == null || !Long.valueOf(tenant).equals(user.getTenantId())) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            if (!CommonStatusEnum.isEnable(user.getStatus())) throw exception(AUTH_LOGIN_USER_DISABLED);
            int updated = users.updateById(new MemberUserDO().setId(user.getId()).setPassword(passwords.encode(request.getPassword())));
            if (updated != 1) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            // Fail closed on RPC errors: local password transaction rolls back, consumed OTP is not restored.
            if (!Boolean.TRUE.equals(tokens.removeAccessToken(user.getId(), UserTypeEnum.MEMBER.getValue()).getCheckedData())) {
                throw exception(MALL_TENANT_UNAVAILABLE);
            }
        });
    }

    private static <T> T inTenant(long tenant, java.util.function.Supplier<T> action) {
        // Runnable overload preserves domain exceptions; the shared Callable overload wraps them.
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        TenantUtils.execute(tenant, () -> { result.set(action.get()); });
        return result.get();
    }
}
