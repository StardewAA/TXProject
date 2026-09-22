package cn.iocoder.txgy.module.product.framework.web;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMemberSalesInterceptorTest {

    private LegacyMemberSalesProperties properties;
    private LegacyMemberSalesInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new LegacyMemberSalesProperties();
        interceptor = new LegacyMemberSalesInterceptor(properties);
    }

    @Test
    void defaultsToBlockingLegacySupplierProductWrite() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> invoke("POST", "/product/supplier-product/save"));
        assertEquals(403, error.getCode());
        assertTrue(error.getMessage().contains("销售中心"));
    }

    @Test
    void blocksOnlySupplierSideQuoteWrites() {
        assertThrows(ServiceException.class,
                () -> invoke("PUT", "/product/purchase-inquiry/quote/submit"));

        MockHttpServletResponse accept = invoke("PUT", "/product/purchase-inquiry/quote/accept");
        MockHttpServletResponse buyerCreate = invoke("POST", "/product/purchase-inquiry/save");
        assertNull(accept.getHeader(LegacyMemberSalesInterceptor.STATE_HEADER));
        assertNull(buyerCreate.getHeader(LegacyMemberSalesInterceptor.STATE_HEADER));
    }

    @Test
    void preservesHistoricalReadsWithExplicitDeprecationHeaders() {
        MockHttpServletResponse response = invoke("GET", "/product/supplier-product/page");
        assertEquals("true", response.getHeader("Deprecation"));
        assertEquals(LegacyMemberSalesInterceptor.READ_ONLY,
                response.getHeader(LegacyMemberSalesInterceptor.STATE_HEADER));
        assertNotNull(response.getHeader("Warning"));
    }

    @Test
    void compatibilitySwitchAllowsOnlyEnumeratedLegacyBoundary() {
        properties.setEnabled(true);
        MockHttpServletResponse response = invoke("POST", "/product/supplier-stock/adjust");
        assertEquals(LegacyMemberSalesInterceptor.COMPATIBILITY_ENABLED,
                response.getHeader(LegacyMemberSalesInterceptor.STATE_HEADER));
    }

    private MockHttpServletResponse invoke(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        return response;
    }
}
