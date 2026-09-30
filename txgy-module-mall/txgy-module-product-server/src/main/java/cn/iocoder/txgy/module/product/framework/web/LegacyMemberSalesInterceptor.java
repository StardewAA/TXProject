package cn.iocoder.txgy.module.product.framework.web;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 将旧 member 供应经营 API 收敛为默认只读。
 *
 * <p>这里按精确边界识别旧供应端写能力，避免误伤仍属于 member 采购中心的询价发布、
 * 接受报价与买卖双方沟通。历史读取暂时保留，并通过标准弃用响应头提示调用方迁移。</p>
 */
@Component
@RequiredArgsConstructor
public class LegacyMemberSalesInterceptor implements HandlerInterceptor {

    static final String STATE_HEADER = "X-Mall-Legacy-Member-Sales";
    static final String READ_ONLY = "read-only";
    static final String COMPATIBILITY_ENABLED = "compatibility-enabled";
    static final String WARNING = "299 txgy \"Legacy member sales API is deprecated; use the system_user business console\"";

    private static final String SUPPLIER_PRODUCT = "/product/supplier-product/";
    private static final String SUPPLIER_SHOP = "/product/supplier-shop/";
    private static final String SUPPLIER_STOCK = "/product/supplier-stock/";
    private static final String QUOTE = "/product/purchase-inquiry/quote/";
    private static final Set<String> LEGACY_QUOTE_WRITES = Set.of("save", "submit", "withdraw");
    private static final Set<String> LEGACY_QUOTE_READS = Set.of("mine", "supplier-page");

    private final LegacyMemberSalesProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = normalizedPath(request.getRequestURI());
        boolean legacySupplierResource = path.contains(SUPPLIER_PRODUCT)
                || path.contains(SUPPLIER_SHOP) || path.contains(SUPPLIER_STOCK);
        boolean legacyQuoteWrite = isLegacyQuoteOperation(path, LEGACY_QUOTE_WRITES)
                && !HttpMethod.GET.matches(request.getMethod());
        boolean legacyWrite = (legacySupplierResource && !HttpMethod.GET.matches(request.getMethod()))
                || legacyQuoteWrite;
        boolean legacyRead = legacySupplierResource && HttpMethod.GET.matches(request.getMethod())
                || isLegacyQuoteOperation(path, LEGACY_QUOTE_READS) && HttpMethod.GET.matches(request.getMethod());

        if (legacyWrite && !properties.isEnabled()) {
            throw new ServiceException(403,
                    "旧会员端销售经营接口已关闭，请使用后台管理的销售中心；采购中心功能不受影响");
        }
        if (legacyWrite) {
            deprecationHeaders(response, COMPATIBILITY_ENABLED);
        } else if (legacyRead) {
            deprecationHeaders(response, READ_ONLY);
        }
        return true;
    }

    private static boolean isLegacyQuoteOperation(String path, Set<String> operations) {
        int quoteIndex = path.indexOf(QUOTE);
        if (quoteIndex < 0) return false;
        String operation = path.substring(quoteIndex + QUOTE.length());
        int slash = operation.indexOf('/');
        if (slash >= 0) operation = operation.substring(0, slash);
        return operations.contains(operation);
    }

    private static String normalizedPath(String path) {
        return path == null ? "" : path.replaceAll("/+$", "");
    }

    private static void deprecationHeaders(HttpServletResponse response, String state) {
        response.setHeader("Deprecation", "true");
        response.setHeader(HttpHeaders.WARNING, WARNING);
        response.setHeader(STATE_HEADER, state);
    }
}
