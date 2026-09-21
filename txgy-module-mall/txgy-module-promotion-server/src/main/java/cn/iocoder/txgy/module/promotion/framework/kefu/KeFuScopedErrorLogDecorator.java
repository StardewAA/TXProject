package cn.iocoder.txgy.module.promotion.framework.kefu;

import cn.iocoder.txgy.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.txgy.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/** Promotion-local fallback for failures before MVC. URL-based protection also survives @Async. */
@Component
@Slf4j
public class KeFuScopedErrorLogDecorator implements BeanPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof ApiErrorLogCommonApi)) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            for (Object value : invocation.getArguments()) {
                if (value instanceof ApiErrorLogCreateReqDTO dto && KeFuScopedPrivacyFilter.matches(dto.getRequestUrl())) {
                    sanitize(dto);
                    log.warn("[scoped-customer-service-error] trace={} code=500 type={} location={}.{}:{}",
                            dto.getTraceId(), dto.getExceptionName(), dto.getExceptionClassName(),
                            dto.getExceptionMethodName(), dto.getExceptionLineNumber());
                }
            }
            return invocation.proceed();
        });
        return proxy.getProxy();
    }

    static void sanitize(ApiErrorLogCreateReqDTO dto) {
        dto.setRequestParams("{}");
        dto.setExceptionMessage("[scoped customer service: message redacted]");
        dto.setExceptionRootCauseMessage("[redacted]");
        dto.setExceptionStackTrace(dto.getExceptionClassName() + "." + dto.getExceptionMethodName()
                + ":" + dto.getExceptionLineNumber());
        // User-Agent is user-controlled and has no diagnostic value for this private channel.
        dto.setUserAgent("scoped-customer-service");
    }
}
