package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductPurchaseMemberApiImplTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsServerVerifiedTenantMemberAndEnterpriseScope() {
        TenantContextHolder.setTenantId(2009L);
        EnterpriseMembershipService membership = mock(EnterpriseMembershipService.class);
        EnterpriseApplicationDO buyer = new EnterpriseApplicationDO();
        buyer.setMemberId(316L);
        buyer.setEnterpriseId(81L);
        buyer.setIdentity("buyer");
        buyer.setStatus(1);
        when(membership.requireBuyer(316L)).thenReturn(buyer);
        ProductPurchaseMemberApiImpl api = new ProductPurchaseMemberApiImpl();
        ReflectionTestUtils.setField(api, "membershipService", membership);

        ProductPurchaseMemberApi.Scope scope = api.getScope(316L).getCheckedData();
        assertEquals(2009L, scope.tenantId());
        assertEquals(316L, scope.memberId());
        assertEquals(81L, scope.enterpriseId());
        assertTrue(api.validateEnterprise(316L, 81L).getCheckedData());
        assertThrows(ServiceException.class, () -> api.validateEnterprise(316L, 82L));
    }
}
