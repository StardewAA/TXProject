package cn.iocoder.txgy.module.promotion.framework.kefu;

import ch.qos.logback.classic.LoggerContext;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.txgy.framework.common.enums.WebFilterOrderEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.util.List;

/** Local privacy boundary; never substitutes, reads, or changes the request body. */
@Component
@Order(WebFilterOrderEnum.ENV_TAG_FILTER + 1)
public class KeFuScopedPrivacyFilter extends OncePerRequestFilter {
    private static final ThreadLocal<Boolean> SCOPED = new ThreadLocal<>();
    private static final List<String> BASES = List.of(
            "/app-api/promotion/kefu-chat", "/admin-api/promotion/kefu-chat",
            "/app-api/promotion/kefu-scope", "/admin-api/promotion/kefu-scope");
    private final KeFuScopedTurboFilter logFilter = new KeFuScopedTurboFilter();
    private LoggerContext logging;
    private final HandlerMethod logOnlyHandler;

    public KeFuScopedPrivacyFilter() {
        try { logOnlyHandler = new HandlerMethod(this, getClass().getMethod("logOnlyMarker")); }
        catch (NoSuchMethodException impossible) { throw new IllegalStateException(impossible); }
    }

    @PostConstruct
    public void install() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            throw new IllegalStateException("Scoped customer service requires the configured Logback privacy guard");
        }
        logging = context;
        logFilter.setContext(context);
        logFilter.start();
        context.addTurboFilter(logFilter);
    }

    @PreDestroy
    public void uninstall() {
        if (logging != null) logging.getTurboFilterList().remove(logFilter);
        logFilter.stop();
    }

    static boolean active() { return Boolean.TRUE.equals(SCOPED.get()); }

    public static boolean matches(String path) {
        if (path == null) return false;
        return BASES.stream().anyMatch(base -> path.equals(base) || path.startsWith(base + "/"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matches(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Boolean previous = SCOPED.get();
        SCOPED.set(true);
        // The framework access logger defaults to logging bodies before authentication has found a handler.
        // Only its private log attribute is set; no Spring routing/security handler attribute is changed.
        request.setAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD, logOnlyHandler);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Pragma", "no-cache");
        try { chain.doFilter(request, response); }
        finally { if (previous == null) SCOPED.remove(); else SCOPED.set(previous); }
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public void logOnlyMarker() { /* Not a route. */ }
}
