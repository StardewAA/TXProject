package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO;
import cn.iocoder.txgy.module.member.service.tenant.MallSmsChallengeService;
import cn.iocoder.txgy.module.member.service.tenant.MallTenantAuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** New protocol is separately gated and anonymous; target policy validates any incoming tenant header. */
@RestController
@RequestMapping("/member/mall-auth")
@RequiredArgsConstructor
@Slf4j
public class AppMallAuthController {
    private final MallSmsChallengeService challenges;
    private final MallTenantAuthService auth;

    @PostMapping("/send-code") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MallSmsChallengeService.Sent> send(@RequestBody @Valid AppMallAuthReqVO.Send body, HttpServletRequest request) {
        return success(safe(() -> challenges.send(body.getTenantId(), body.getMobile(), body.getPurpose(), request.getRemoteAddr())));
    }
    @PostMapping("/register") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppAuthLoginRespVO> register(@RequestBody @Valid AppMallAuthReqVO.Register body, HttpServletRequest request) {
        return success(safe(() -> auth.register(body, request.getRemoteAddr())));
    }
    @PostMapping("/sms-login") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppAuthLoginRespVO> smsLogin(@RequestBody @Valid AppMallAuthReqVO.Challenge body, HttpServletRequest request) {
        return success(safe(() -> auth.smsLogin(body, request.getRemoteAddr())));
    }
    @PostMapping("/login") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppAuthLoginRespVO> login(@RequestBody @Valid AppMallAuthReqVO.PasswordLogin body, HttpServletRequest request) {
        return success(safe(() -> auth.passwordLogin(body, request.getRemoteAddr())));
    }
    @PostMapping("/reset-password") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Boolean> reset(@RequestBody @Valid AppMallAuthReqVO.ResetPassword body, HttpServletRequest request) {
        return success(safe(() -> { auth.resetPassword(body, request.getRemoteAddr()); return true; }));
    }

    private static <T> T safe(java.util.function.Supplier<T> operation) {
        try { return operation.get(); }
        catch (cn.iocoder.txgy.framework.common.exception.ServiceException error) { throw error; }
        catch (RuntimeException error) {
            // Shared unknown-error auditing stores raw request bodies. Contain auth infrastructure faults here.
            log.warn("商城认证基础设施异常，类型={}；请求内容及凭据不记录", error.getClass().getSimpleName());
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE);
        }
    }
}
