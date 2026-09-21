package cn.iocoder.txgy.module.product.controller.app.portal;

import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppMallPortalControllerContractTest {

    @Test
    void exposesPortalListDetailAndTenantRoutesAsAnonymousCrossTenantReads() {
        RequestMapping base = AppMallPortalController.class.getAnnotation(RequestMapping.class);
        assertEquals(Set.of("/mall"), Set.of(base.value()));
        assertNotNull(AppMallPortalController.class.getAnnotation(PermitAll.class));
        assertNotNull(AppMallPortalController.class.getAnnotation(TenantIgnore.class));

        Set<String> routes = Arrays.stream(AppMallPortalController.class.getDeclaredMethods())
                .map(AppMallPortalControllerContractTest::getPath)
                .collect(Collectors.toSet());
        assertEquals(Set.of("/portal/products", "/portal/products/{spuId}", "/portal/tenants/hot",
                "/tenants/{tenantSlug}", "/tenants/{tenantSlug}/products",
                "/tenants/{tenantSlug}/products/{spuId}"), routes);
    }

    private static String getPath(Method method) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping, method.getName() + " must remain a GET endpoint");
        assertEquals(1, mapping.value().length);
        return mapping.value()[0];
    }
}
