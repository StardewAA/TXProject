package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAfterSaleRefundCallbackServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void routesPaymentTenantCallbackToSellerTenant() {
        TenantContextHolder.setTenantId(2009L);
        AfterSaleMapper mapper = mock(AfterSaleMapper.class);
        AfterSaleService afterSales = mock(AfterSaleService.class);
        when(mapper.selectTenantIdByIdCrossTenant(125L)).thenReturn(2024L);
        doAnswer(invocation -> {
            assertEquals(2024L, TenantContextHolder.getRequiredTenantId());
            return null;
        }).when(afterSales).updateAfterSaleRefunded(125L, 940728L, 55L);

        new PlatformAfterSaleRefundCallbackService(mapper, afterSales)
                .updateRefunded(125L, 940728L, 55L);

        assertEquals(2009L, TenantContextHolder.getRequiredTenantId());
    }
}
