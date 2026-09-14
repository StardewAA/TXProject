package cn.iocoder.txgy.module.bidding.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪过滤器：为每个请求准备 traceId（响应头 + MDC）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§0.3（响应头 X-Trace-Id）、§0.4（错误体 traceId）。
 * <p>设计遵循/针对：① 优先复用上游传入的 X-Trace-Id（网关/前端已有时不另起新 ID，保证全链路同一 ID）；
 * ② 只在没有时生成，避免覆盖上游导致链路断裂；③ finally 中清理 MDC，防止线程池复用导致 ID 串号
 * （这是追踪实现最常见的 bug：异步/线程池场景下旧 ID 泄漏到下一个请求的日志里）。
 * <p>业务目标：任何一次请求的日志都能唯一定位，前端报错时可直接把 traceId 反馈给运维。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BiddingTraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(BiddingTraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = BiddingTraceContext.generate();
        }
        MDC.put(BiddingTraceContext.MDC_KEY, traceId);
        response.setHeader(BiddingTraceContext.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(BiddingTraceContext.MDC_KEY);
        }
    }
}
