package cn.iocoder.txgy.module.promotion.framework.kefu;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cn.iocoder.txgy.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.txgy.framework.web.core.handler.GlobalExceptionHandler;
import org.slf4j.Marker;

/** Runs before async appenders capture an event; only active for the four new scoped routes. */
final class KeFuScopedTurboFilter extends TurboFilter {
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!KeFuScopedPrivacyFilter.active()) return FilterReply.NEUTRAL;
        if (logger.getName().equals(ApiAccessLogInterceptor.class.getName())
                && format != null && format.startsWith("[preHandle]")) return FilterReply.DENY;
        // MVC advice emits safe error metadata. Pre-MVC fallback is sanitized at the error-log API boundary.
        if (logger.getName().equals(GlobalExceptionHandler.class.getName())) return FilterReply.DENY;
        return FilterReply.NEUTRAL;
    }
}
