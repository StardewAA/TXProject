package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.service.order.EnterprisePurchaseOrderService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.AFTER_SALE_ENTERPRISE_ACTION_REQUIRES_ORDER_CREATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnterprisePurchaseAfterSaleServiceTest {

    private EnterprisePurchaseOrderService enterpriseOrders;
    private TradeOrderQueryService orderQueries;
    private AfterSaleMapper afterSales;
    private AfterSaleService personalAfterSales;
    private EnterprisePurchaseAfterSaleService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        enterpriseOrders = mock(EnterprisePurchaseOrderService.class);
        orderQueries = mock(TradeOrderQueryService.class);
        afterSales = mock(AfterSaleMapper.class);
        personalAfterSales = mock(AfterSaleService.class);
        service = new EnterprisePurchaseAfterSaleService();
        ReflectionTestUtils.setField(service, "enterpriseOrders", enterpriseOrders);
        ReflectionTestUtils.setField(service, "orderQueryService", orderQueries);
        ReflectionTestUtils.setField(service, "afterSaleMapper", afterSales);
        ReflectionTestUtils.setField(service, "afterSaleService", personalAfterSales);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void pageUsesServerVerifiedTenantAndEnterprise() {
        AppAfterSalePageReqVO request = new AppAfterSalePageReqVO().setStatuses(Set.of(10));
        PageResult<AfterSaleDO> expected = new PageResult<>(List.of(afterSale(1L, 11L, 317L)), 1L);
        when(enterpriseOrders.requireEnterpriseId(316L)).thenReturn(81L);
        when(afterSales.selectEnterpriseInquiryPage(request, 2009L, 81L)).thenReturn(expected);

        assertSame(expected, service.getPage(316L, request));
        verify(afterSales).selectEnterpriseInquiryPage(request, 2009L, 81L);
    }

    @Test
    void colleagueCanReadButCannotCreateRefundFact() {
        AfterSaleDO afterSale = afterSale(5L, 11L, 317L);
        when(afterSales.selectById(5L)).thenReturn(afterSale);
        when(enterpriseOrders.getOrder(316L, 11L)).thenReturn(inquiry(11L, 317L));

        EnterprisePurchaseAfterSaleService.AfterSaleAccess access = service.get(316L, 5L);
        assertSame(afterSale, access.afterSale());
        assertFalse(access.createdByCurrentMember());

        AppAfterSaleCreateReqVO request = new AppAfterSaleCreateReqVO().setOrderItemId(8L);
        when(orderQueries.getOrderItem(8L)).thenReturn(item(8L, 11L, 317L));
        ServiceException error = assertThrows(ServiceException.class, () -> service.create(316L, request));
        assertEquals(AFTER_SALE_ENTERPRISE_ACTION_REQUIRES_ORDER_CREATOR.getCode(), error.getCode());
        verifyNoInteractions(personalAfterSales);
    }

    @Test
    void actualCreatorCreatesWithOriginalPaymentIdentity() {
        AppAfterSaleCreateReqVO request = new AppAfterSaleCreateReqVO().setOrderItemId(8L);
        when(orderQueries.getOrderItem(8L)).thenReturn(item(8L, 11L, 316L));
        when(enterpriseOrders.getOrder(316L, 11L)).thenReturn(inquiry(11L, 316L));
        when(personalAfterSales.createAfterSale(316L, request)).thenReturn(91L);

        assertEquals(91L, service.create(316L, request));
        verify(personalAfterSales).createAfterSale(316L, request);
    }

    @Test
    void colleagueCannotCancelOrSubmitReturnShipment() {
        AfterSaleDO afterSale = afterSale(5L, 11L, 317L);
        when(afterSales.selectById(5L)).thenReturn(afterSale);
        when(enterpriseOrders.getOrder(316L, 11L)).thenReturn(inquiry(11L, 317L));

        assertThrows(ServiceException.class, () -> service.cancel(316L, 5L));
        AppAfterSaleDeliveryReqVO delivery = new AppAfterSaleDeliveryReqVO().setId(5L)
                .setLogisticsId(2L).setLogisticsNo("SF1");
        assertThrows(ServiceException.class, () -> service.delivery(316L, delivery));

        verify(personalAfterSales, never()).cancelAfterSale(317L, 5L);
        verify(personalAfterSales, never()).deliveryAfterSale(317L, delivery);
    }

    @Test
    void corruptedOwnerMismatchFailsClosed() {
        when(afterSales.selectById(5L)).thenReturn(afterSale(5L, 11L, 999L));
        when(enterpriseOrders.getOrder(316L, 11L)).thenReturn(inquiry(11L, 316L));

        assertThrows(ServiceException.class, () -> service.get(316L, 5L));
        verifyNoInteractions(personalAfterSales);
    }

    private static TradeOrderDO inquiry(Long id, Long creator) {
        return new TradeOrderDO().setId(id).setUserId(creator)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY).setBuyerEnterpriseId(81L);
    }

    private static TradeOrderItemDO item(Long id, Long orderId, Long owner) {
        return new TradeOrderItemDO().setId(id).setOrderId(orderId).setUserId(owner);
    }

    private static AfterSaleDO afterSale(Long id, Long orderId, Long owner) {
        return new AfterSaleDO().setId(id).setOrderId(orderId).setUserId(owner);
    }
}
