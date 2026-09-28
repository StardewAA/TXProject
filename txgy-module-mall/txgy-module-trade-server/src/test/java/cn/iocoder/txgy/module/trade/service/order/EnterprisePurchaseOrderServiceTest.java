package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.enterprise.ProductPurchaseMemberApi;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderUpdateAddressReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.service.delivery.TradeOrderLogisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnterprisePurchaseOrderServiceTest {

    private ProductPurchaseMemberApi members;
    private TradeOrderMapper orders;
    private TradeOrderUpdateService updates;
    private EnterprisePurchaseOrderService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        members = mock(ProductPurchaseMemberApi.class);
        orders = mock(TradeOrderMapper.class);
        updates = mock(TradeOrderUpdateService.class);
        service = new EnterprisePurchaseOrderService();
        ReflectionTestUtils.setField(service, "purchaseMemberApi", members);
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "orderUpdateService", updates);
        ReflectionTestUtils.setField(service, "logisticsService", mock(TradeOrderLogisticsService.class));
        when(members.getScope(316L)).thenReturn(success(new ProductPurchaseMemberApi.Scope(2009L, 316L, 81L)));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void pageIsAlwaysFilteredByServerVerifiedEnterprise() {
        AppTradeOrderPageReqVO request = new AppTradeOrderPageReqVO();
        PageResult<TradeOrderDO> expected = new PageResult<>(List.of(inquiry(1L, 317L, 81L)), 1L);
        when(orders.selectEnterpriseInquiryPage(request, 81L)).thenReturn(expected);

        assertSame(expected, service.getPage(316L, request));
        verify(orders).selectEnterpriseInquiryPage(request, 81L);
    }

    @Test
    void anotherActiveMemberInSameEnterpriseCanReceiveUsingActualCreatorIdentity() {
        TradeOrderDO order = inquiry(11L, 317L, 81L);
        when(orders.selectById(11L)).thenReturn(order);

        service.receive(316L, 11L);

        verify(updates).receiveOrderByMember(317L, 11L);
    }

    @Test
    void crossEnterpriseAndNormalOrdersAreIndistinguishablyRejected() {
        when(orders.selectById(12L)).thenReturn(inquiry(12L, 317L, 82L));
        when(orders.selectById(13L)).thenReturn(new TradeOrderDO().setId(13L).setUserId(316L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_NORMAL));

        ServiceException cross = assertThrows(ServiceException.class, () -> service.getOrder(316L, 12L));
        ServiceException normal = assertThrows(ServiceException.class, () -> service.getOrder(316L, 13L));
        assertEquals(cross.getCode(), normal.getCode());
        verifyNoInteractions(updates);
    }

    @Test
    void addressChangeCarriesActorAndVerifiedEnterpriseToAtomicUpdate() {
        when(orders.selectById(14L)).thenReturn(inquiry(14L, 317L, 81L));
        AppTradeOrderUpdateAddressReqVO request = new AppTradeOrderUpdateAddressReqVO()
                .setId(14L).setAddressId(91L);

        service.updateAddress(316L, request);

        verify(updates).updateInquiryOrderAddressByEnterprise(316L, 81L, request);
    }

    @Test
    void tenantMismatchInRemoteScopeFailsClosedBeforeReadingOrders() {
        when(members.getScope(316L)).thenReturn(success(new ProductPurchaseMemberApi.Scope(1L, 316L, 81L)));

        assertThrows(ServiceException.class, () -> service.getOrder(316L, 11L));
        verifyNoInteractions(orders, updates);
    }

    private static TradeOrderDO inquiry(Long id, Long creator, Long enterprise) {
        return new TradeOrderDO().setId(id).setUserId(creator)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY).setBuyerEnterpriseId(enterprise);
    }
}
