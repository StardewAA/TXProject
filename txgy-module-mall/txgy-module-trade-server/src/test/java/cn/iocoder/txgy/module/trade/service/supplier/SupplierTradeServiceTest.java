package cn.iocoder.txgy.module.trade.service.supplier;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.dal.mysql.order.*;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import static cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService.AfterSaleAction.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupplierTradeServiceTest {
    SupplierTradeService service;
    ProductSupplierApi auth;
    TradeOrderMapper orders;
    TradeOrderItemMapper items;
    TradeOrderUpdateService updates;
    TradeOrderDO order;
    AfterSaleMapper afterSales;
    AfterSaleService afterUpdate;
    AfterSaleDO sale;
    @BeforeEach void setup() {
        service=new SupplierTradeService();auth=mock(ProductSupplierApi.class);orders=mock(TradeOrderMapper.class);
        ReflectionTestUtils.setField(service,"erpInventory",
                mock(cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService.class));
        items=mock(TradeOrderItemMapper.class);updates=mock(TradeOrderUpdateService.class);
        ReflectionTestUtils.setField(service,"authorization",auth);ReflectionTestUtils.setField(service,"orders",orders);
        ReflectionTestUtils.setField(service,"items",items);ReflectionTestUtils.setField(service,"orderUpdate",updates);
        afterSales=mock(AfterSaleMapper.class);afterUpdate=mock(AfterSaleService.class);
        ReflectionTestUtils.setField(service,"afterSales",afterSales);
        ReflectionTestUtils.setField(service,"afterSaleUpdate",afterUpdate);
        sale=new AfterSaleDO().setId(40L).setShopId(10L).setOrderId(20L).setOrderItemId(30L).setStatus(10).setWay(10);
        when(afterSales.selectOne(any())).thenReturn(sale);
        when(items.selectById(30L)).thenReturn(new TradeOrderItemDO().setId(30L).setOrderId(20L).setShopId(10L).setAfterSaleId(40L).setAfterSaleStatus(10));
        ReflectionTestUtils.setField(service,"expressService",mock(DeliveryExpressService.class));
        when(auth.validateShop(1L,10L)).thenReturn(CommonResult.success(true));
        order=new TradeOrderDO().setId(20L).setShopId(10L).setStatus(10).setPayStatus(true).setDeliveryType(1).setRefundStatus(0);
        when(orders.selectOne(any())).thenReturn(order);
        when(items.selectListByOrderId(20L)).thenReturn(List.of(new TradeOrderItemDO().setId(30L).setShopId(10L).setAfterSaleStatus(0)));
    }
    @Test void permissionFailureStopsReads() {
        when(auth.validateShop(1L,10L)).thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class,()->service.page(1L,10L,new PageParam(),null,null));
        verifyNoInteractions(orders);
    }
    @Test void permissionServiceFailureIsClosed() {
        when(auth.validateShop(1L,10L)).thenThrow(new RuntimeException("offline"));
        assertThrows(RuntimeException.class,()->service.deliver(1L,10L,20L,1L,"QA12345"));
        verifyNoInteractions(updates);
    }
    @Test void foreignOrderDenied() {
        when(orders.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class,()->service.detail(1L,10L,99L));
        verifyNoInteractions(items);
    }
    @Test void mixedShopItemsDenied() {
        when(items.selectListByOrderId(20L)).thenReturn(List.of(new TradeOrderItemDO().setShopId(99L)));
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"QA12345"));
        verifyNoInteractions(updates);
    }
    @Test void unpaidAndCanceledDenied() {
        order.setPayStatus(false);
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"QA12345"));
        order.setPayStatus(true).setStatus(40);
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"QA12345"));
        verifyNoInteractions(updates);
    }
    @Test void afterSaleBlocksDelivery() {
        when(items.selectListByOrderId(20L)).thenReturn(List.of(new TradeOrderItemDO().setShopId(10L).setAfterSaleStatus(10)));
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"QA12345"));
        verifyNoInteractions(updates);
    }
    @Test void validDeliveryDelegatesOnce() {
        service.deliver(1L,10L,20L,1L,"QA12345");
        verify(updates).deliveryOrder(argThat(r->r.getId().equals(20L)&&r.getLogisticsId().equals(1L)&&r.getLogisticsNo().equals("QA12345")));
    }
    @Test void identicalRetryIsIdempotentAndDifferentTrackingDenied() {
        order.setStatus(20).setLogisticsId(1L).setLogisticsNo("QA12345");
        service.deliver(1L,10L,20L,1L,"QA12345");
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"QA54321"));
        verifyNoInteractions(updates);
    }
    @Test void noLogisticsAndInvalidTrackingDenied() {
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,0L,"QA12345"));
        assertThrows(ServiceException.class,()->service.deliver(1L,10L,20L,1L,"<script>"));
        verifyNoInteractions(updates);
    }
    @Test void afterSalePermissionDenialStopsReadsAndWrites() {
        when(auth.validateShop(1L,10L)).thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,AGREE,null));
        assertThrows(ServiceException.class,()->service.afterSaleDetail(1L,10L,40L));
        verifyNoInteractions(afterSales,afterUpdate);
    }
    @Test void missingAndForeignAfterSaleItemDenied() {
        when(afterSales.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,AGREE,null));
        when(afterSales.selectOne(any())).thenReturn(sale);
        when(items.selectById(30L)).thenReturn(new TradeOrderItemDO().setShopId(99L).setOrderId(20L));
        assertThrows(ServiceException.class,()->service.afterSaleDetail(1L,10L,40L));
        verifyNoInteractions(afterUpdate);
    }
    @Test void staleAfterSaleLinkDenied() {
        when(items.selectById(30L)).thenReturn(new TradeOrderItemDO().setShopId(10L).setOrderId(20L).setAfterSaleId(41L).setAfterSaleStatus(10));
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,AGREE,null));
        verifyNoInteractions(afterUpdate);
    }
    @Test void approvalDoesNotImpersonateAdministratorOrRefund() {
        service.processAfterSale(1L,10L,40L,AGREE,null);
        verify(afterUpdate).agreeAfterSale(null,40L);
        verifyNoMoreInteractions(afterUpdate);
    }
    @Test void returnApprovalRequiresAddress() {
        sale.setWay(20);when(auth.returnAddress(1L,10L)).thenReturn(CommonResult.success(null));
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,AGREE,null));
        verifyNoInteractions(afterUpdate);
        verify(afterSales,never()).updateById(any(AfterSaleDO.class));
    }
    @Test void returnApprovalCopiesAddressBeforeTransition() {
        sale.setWay(20);when(auth.returnAddress(1L,10L)).thenReturn(CommonResult.success(new ProductSupplierApi.ReturnAddress("收件人","123456","测试退货地址")));
        service.processAfterSale(1L,10L,40L,AGREE,null);
        var ordered=inOrder(afterSales,afterUpdate);
        ordered.verify(afterSales).updateById(argThat((AfterSaleDO a)->a.getId().equals(40L)&&a.getReturnAddress().equals("测试退货地址")&&a.getReturnReceiverName().equals("收件人")));
        ordered.verify(afterUpdate).agreeAfterSale(null,40L);
    }
    @Test void refusalRequiresReasonAndTrimsIt() {
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,REJECT,"  "));
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,REJECT,"字".repeat(201)));
        service.processAfterSale(1L,10L,40L,REJECT,"  凭证不完整  ");
        verify(afterUpdate).disagreeAfterSale(isNull(),argThat(r->r.getId().equals(40L)&&r.getAuditReason().equals("凭证不完整")));
        verifyNoMoreInteractions(afterUpdate);
    }
    @Test void staleStatusAndPrematureReceiveDenied() {
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,RECEIVE,null));
        sale.setStatus(40);
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,AGREE,null));
        verifyNoInteractions(afterUpdate);
    }
    @Test void receiveAndRefuseOnlyReturnRefundAndNeverPay() {
        sale.setStatus(30);
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,RECEIVE,null));
        sale.setWay(20);
        service.processAfterSale(1L,10L,40L,RECEIVE,null);
        assertThrows(ServiceException.class,()->service.processAfterSale(1L,10L,40L,REFUSE," "));
        service.processAfterSale(1L,10L,40L,REFUSE,"退货不符");
        verify(afterUpdate).receiveAfterSale(1L,40L);
        verify(afterUpdate).refuseAfterSale(eq(1L),argThat(r->r.getRefuseMemo().equals("退货不符")));
        verifyNoMoreInteractions(afterUpdate);
    }
}
