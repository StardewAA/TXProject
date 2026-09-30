package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.service.order.PlatformBuyerOrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_ITEM_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformBuyerAfterSaleServiceTest {

    private AfterSaleMapper afterSales;
    private AfterSaleService personalAfterSales;
    private PlatformBuyerOrderQueryService buyerOrders;
    private PlatformBuyerAfterSaleService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        afterSales = mock(AfterSaleMapper.class);
        personalAfterSales = mock(AfterSaleService.class);
        buyerOrders = mock(PlatformBuyerOrderQueryService.class);
        service = new PlatformBuyerAfterSaleService(afterSales, personalAfterSales, buyerOrders);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void pageUsesPlatformOwnershipAcrossTenants() {
        AppAfterSalePageReqVO request = new AppAfterSalePageReqVO().setStatuses(Set.of(10));
        AfterSaleDO row = new AfterSaleDO().setId(7L);
        when(buyerOrders.platformAccountId(31L, 2009L)).thenReturn(81L);
        when(afterSales.selectAppBuyerPage(31L, 2009L, 81L, Set.of(10), 0L, 10))
                .thenReturn(List.of(row));
        when(afterSales.selectAppBuyerCount(31L, 2009L, 81L, Set.of(10))).thenReturn(1L);

        PageResult<AfterSaleDO> result = service.page(31L, 2009L, request);

        assertEquals(1L, result.getTotal());
        assertSame(row, result.getList().get(0));
    }

    @Test
    void createSwitchesToAuthorizedSellerTenantAndRestoresSourceTenant() {
        AppAfterSaleCreateReqVO request = new AppAfterSaleCreateReqVO().setOrderItemId(88L);
        when(buyerOrders.itemTenant(88L, 31L, 2009L)).thenReturn(2024L);
        when(personalAfterSales.createAfterSale(31L, request)).thenAnswer(invocation -> {
            assertEquals(2024L, TenantContextHolder.getRequiredTenantId());
            return 99L;
        });

        assertEquals(99L, service.create(31L, 2009L, request));
        assertEquals(2009L, TenantContextHolder.getRequiredTenantId());
        verify(personalAfterSales).createAfterSale(31L, request);
    }

    @Test
    void createRejectsUnownedOrderItem() {
        AppAfterSaleCreateReqVO request = new AppAfterSaleCreateReqVO().setOrderItemId(88L);
        when(buyerOrders.itemTenant(88L, 31L, 2009L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(31L, 2009L, request));

        assertEquals(ORDER_ITEM_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(personalAfterSales);
    }
}
