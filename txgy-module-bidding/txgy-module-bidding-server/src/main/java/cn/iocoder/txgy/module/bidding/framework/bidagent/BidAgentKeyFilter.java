package cn.iocoder.txgy.module.bidding.framework.bidagent;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 标书 Agent 机对机鉴权过滤器：校验 X-Agent-Key 请求头。
 *
 * 配合 application.yaml 里 txgy.security.permit-all_urls 放行 /admin-api/bid/agent/**，
 * 用内部密钥替代登录 token 完成鉴权。agentKey 为空时（开发态）不校验。
 */
public class BidAgentKeyFilter implements Filter {

    public static final String HEADER_NAME = "X-Agent-Key";

    private final String agentKey;

    public BidAgentKeyFilter(String agentKey) {
        this.agentKey = agentKey;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        String provided = httpRequest.getHeader(HEADER_NAME);
        if (StrUtil.isBlank(agentKey) || agentKey.equals(provided)) {
            chain.doFilter(request, response);
            return;
        }
        // 密钥不匹配：拒绝
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getOutputStream()
                .write("{\"code\":401,\"msg\":\"invalid agent key\"}".getBytes(StandardCharsets.UTF_8));
    }
}
