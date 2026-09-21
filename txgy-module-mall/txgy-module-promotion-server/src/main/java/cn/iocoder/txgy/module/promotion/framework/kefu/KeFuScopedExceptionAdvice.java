package cn.iocoder.txgy.module.promotion.framework.kefu;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.monitor.TracerUtils;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuScopeController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuScopeController;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.*;

/** The transaction proxy has already rolled back before this response translation is invoked. */
@RestControllerAdvice(assignableTypes = {AppKeFuChatController.class, KeFuChatController.class,
        AppKeFuScopeController.class, KeFuScopeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class KeFuScopedExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public CommonResult<?> handle(Exception exception, HttpServletResponse response) {
        ErrorCode safe = safeCode(exception);
        String trace = TracerUtils.getTraceId();
        if (trace == null || trace.isBlank()) trace = UUID.randomUUID().toString();
        response.setHeader("X-Kefu-Trace-Id", trace);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Pragma", "no-cache");
        // Do not log exception.toString(), its message, causes, rejected values, or the full stack.
        log.warn("[scoped-customer-service] trace={} code={} type={} location={}", trace, safe.getCode(),
                exception.getClass().getName(), safeLocation(exception));
        return CommonResult.error(safe);
    }

    static ErrorCode safeCode(Exception exception) {
        if (exception instanceof ServiceException business) {
            // Codes are fixed and public. Never forward an interpolated upstream message.
            for (ErrorCode safe : List.of(KeFuScopeService.DISABLED, KeFuScopeService.INVALID,
                    KeFuScopeService.FORBIDDEN, KeFuScopeService.CONFLICT, BAD_REQUEST, UNAUTHORIZED,
                    FORBIDDEN, LOCKED, TOO_MANY_REQUESTS)) {
                if (safe.getCode().equals(business.getCode())) return safe;
            }
            return INTERNAL_SERVER_ERROR;
        }
        if (exception instanceof AuthenticationException) return UNAUTHORIZED;
        if (exception instanceof AccessDeniedException) return FORBIDDEN;
        if (exception instanceof HttpMessageNotReadableException || exception instanceof BindException
                || exception instanceof ValidationException || exception instanceof MissingServletRequestParameterException
                || exception instanceof MethodArgumentTypeMismatchException) return BAD_REQUEST;
        return INTERNAL_SERVER_ERROR;
    }

    static String safeLocation(Throwable exception) {
        StackTraceElement[] stack = exception.getStackTrace();
        if (stack.length == 0) return "unknown";
        StackTraceElement point = stack[0];
        return point.getClassName() + "." + point.getMethodName() + ":" + point.getLineNumber();
    }
}
