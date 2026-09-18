package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class MallImageAuthService {
    private final MallAccountPolicy policy;
    private final MallImageChallengeService challenges;
    private final MemberAuthService auth;

    public MallImageChallengeService.Challenge issue(AppMallImageAuthReqVO.Image body, String peer) {
        long tenant = policy.resolve(body.getTenantId(), true);
        return challenges.issue(tenant, MallAccountPolicy.normalize(body.getLoginName()), body.getCaptchaSession(),
                body.getPreviousChallengeId(), body.getPurpose(), peer);
    }
    public AppAuthLoginRespVO register(AppMallImageAuthReqVO.Register body, String peer) {
        long tenant = policy.resolve(body.getTenantId(), true);
        body.setLoginName(MallAccountPolicy.normalize(body.getLoginName()));
        if (!Boolean.TRUE.equals(body.getAgree()) || !Objects.equals(body.getPassword(), body.getConfirmPassword()))
            throw exception(new ErrorCode(1_004_008_193, "请确认协议且两次密码一致"));
        // Consumed outside the DB transaction: rollback must never resurrect a used captcha.
        challenges.consume(tenant, body.getLoginName(), body.getCaptchaSession(), body.getImageChallengeId(), body.getImageCode(), peer);
        try { return inTenant(tenant, () -> auth.registerWithVerifiedImage(body)); }
        catch (DuplicateKeyException error) { throw exception(MallAccountPolicy.ACCOUNT_USED); }
    }
    public AppAuthLoginRespVO login(AppMallImageAuthReqVO.Login body, String peer) {
        long tenant = policy.resolve(body.getTenantId(), false);
        String account = body.getAccount().trim().toLowerCase(java.util.Locale.ROOT);
        challenges.limitPasswordAttempts(account, peer);
        return inTenant(tenant, () -> auth.loginWithAccountPassword(account, body.getPassword()));
    }
    private static <T> T inTenant(long tenant, Supplier<T> task) {
        AtomicReference<T> value = new AtomicReference<>();
        TenantUtils.execute(tenant, (Runnable) () -> value.set(task.get()));
        return value.get();
    }
}
