package cn.iocoder.txgy.module.product.controller.admin.portal;

import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantPageReqVO;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantUpdateReqVO;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalTenantAdminMapper;
import cn.iocoder.txgy.module.product.service.portal.MallPortalTenantAdminService;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallPortalTenantAdminContractTest {

    @Test
    void controllerIsPermissionProtectedAndDoesNotExposeCreateOrDelete() throws Exception {
        RequestMapping mapping = MallPortalTenantAdminController.class.getAnnotation(RequestMapping.class);
        assertEquals("/product/portal-tenant", mapping.value()[0]);
        assertNotNull(MallPortalTenantAdminController.class.getAnnotation(TenantIgnore.class));

        Method page = MallPortalTenantAdminController.class.getMethod(
                "getPage", MallPortalTenantPageReqVO.class);
        Method get = MallPortalTenantAdminController.class.getMethod("get", Long.class);
        Method update = MallPortalTenantAdminController.class.getMethod(
                "update", MallPortalTenantUpdateReqVO.class);
        assertEquals("@ss.hasPermission('product:portal-tenant:query')",
                page.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('product:portal-tenant:query')",
                get.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('product:portal-tenant:update')",
                update.getAnnotation(PreAuthorize.class).value());
        assertFalse(Arrays.stream(MallPortalTenantAdminController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class)));
    }

    @Test
    void serviceAddsPlatformTenantBoundaryBeyondMenuPermission() throws Exception {
        Method guard = MallPortalTenantAdminService.class.getDeclaredMethod(
                "requirePlatformAdminTenant");
        assertNotNull(guard);
    }

    @Test
    void mapperExplicitlyCrossesTenantBoundaryButUpdatePinsOriginalOwnership() throws Exception {
        InterceptorIgnore ignore = MallPortalTenantAdminMapper.class.getAnnotation(InterceptorIgnore.class);
        assertNotNull(ignore);
        assertEquals("true", ignore.tenantLine());

        Method updateMethod = MallPortalTenantAdminMapper.class.getMethod(
                "updatePortalConfig",
                cn.iocoder.txgy.module.product.dal.dataobject.portal.MallPortalTenantDO.class,
                String.class);
        String updateSql = String.join(" ", updateMethod.getAnnotation(Update.class).value());
        assertTrue(updateSql.contains("WHERE id=#{row.id} AND tenant_id=#{row.tenantId}"));
        assertFalse(updateSql.contains("SET tenant_id="));

        Method tenantMethod = MallPortalTenantAdminMapper.class.getMethod(
                "selectSystemTenant", Long.class);
        String tenantSql = String.join(" ", tenantMethod.getAnnotation(Select.class).value());
        assertTrue(tenantSql.contains("FROM system_tenant"));
        assertTrue(tenantSql.contains("WHERE id=#{tenantId}"));
    }
}
