package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO;
import cn.iocoder.txgy.module.member.service.tenant.MallImageAuthService;
import cn.iocoder.txgy.module.member.service.tenant.MallImageChallengeService;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.function.Supplier;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;

@RestController
@RequestMapping("/member/mall-auth")
@RequiredArgsConstructor
@Slf4j
public class AppMallImageAuthController {
    private final MallImageAuthService service;

    @PostMapping("/image-challenge") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MallImageChallengeService.Challenge> image(@Valid @RequestBody AppMallImageAuthReqVO.Image body, HttpServletRequest request) {
        return success(safe(() -> service.issue(body, request.getRemoteAddr())));
    }
    @PostMapping("/register-image") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppAuthLoginRespVO> register(@Valid @RequestBody AppMallImageAuthReqVO.Register body, HttpServletRequest request) {
        return success(safe(() -> service.register(body, request.getRemoteAddr())));
    }
    @PostMapping("/account-login") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppAuthLoginRespVO> login(@Valid @RequestBody AppMallImageAuthReqVO.Login body, HttpServletRequest request) {
        return success(safe(() -> service.login(body, request.getRemoteAddr())));
    }

    // Binding/deserialization happens before the endpoint and safe(). Keep credentials and rejected field
    // values out of the shared exception logger, which otherwise records exceptions and raw request bodies.
    // BindException also covers MethodArgumentNotValidException from @Valid request bodies.
    @ExceptionHandler({BindException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    public CommonResult<?> invalidCredentialRequest() {
        return CommonResult.error(400, "认证请求参数不正确，请检查后重试");
    }

    private static <T> T safe(Supplier<T> action) {
        try { return action.get(); }
        catch (ServiceException error) { throw error; }
        catch (RuntimeException error) {
            log.warn("商城图片认证暂不可用，异常类型={}；不记录请求凭据", error.getClass().getSimpleName());
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
    }
}
