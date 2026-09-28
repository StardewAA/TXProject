package cn.iocoder.txgy.module.bidding.framework.bidagent;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 门户标书工坊租户兜底拦截器。
 *
 * <p>个人/平台租户（tenant=0）在门户使用标书工坊时，把标书 Agent 管理端接口的执行上下文
 * 切换到配置的企业租户（bidding.bid-agent.portal-enterprise-tenant-id，如西安欧鹏=1），
 * 使材料入库、生成任务台账、知识库检索、企业事实、产物下载全部落在企业租户下；
 * 企业账号（tenant>0）请求不受影响。
 */
@Component
public class BidAgentPortalTenantInterceptor implements HandlerInterceptor {

    private static final String ATTR_PREV_TENANT = BidAgentPortalTenantInterceptor.class.getName() + ".prevTenant";
    private static final String ATTR_PREV_IGNORE = BidAgentPortalTenantInterceptor.class.getName() + ".prevIgnore";

    @Resource
    private BidAgentProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long current = TenantContextHolder.getTenantId();
        Long enterprise = properties.getPortalEnterpriseTenantId();
        if (enterprise == null || (current != null && current != 0)) {
            return true;
        }
        request.setAttribute(ATTR_PREV_TENANT, current);
        request.setAttribute(ATTR_PREV_IGNORE, TenantContextHolder.isIgnore());
        TenantContextHolder.setTenantId(enterprise);
        TenantContextHolder.setIgnore(false);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Object prevTenant = request.getAttribute(ATTR_PREV_TENANT);
        Object prevIgnore = request.getAttribute(ATTR_PREV_IGNORE);
        if (prevTenant == null && prevIgnore == null) {
            return;
        }
        TenantContextHolder.setTenantId((Long) prevTenant);
        TenantContextHolder.setIgnore((Boolean) prevIgnore);
    }
}
