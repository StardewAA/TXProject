package cn.iocoder.txgy.module.trade.service.erp;

import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.dal.mysql.order.*;
import cn.iocoder.txgy.module.trade.service.order.*;
import cn.iocoder.txgy.module.trade.service.order.handler.*;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;

class TradeErpOrderFlowTest {
    TradeErpInventoryService erp;
    TradeOrderMapper orders;
    TradeOrderItemMapper items;
    TradeOrderUpdateServiceImpl update;
    TradeOrderDO order;
    TradeOrderItemDO item;
    Entry entry(String op,String status){
        return new Entry("req-1",1L,"O-1",op,"RETURN".equals(op)?"5":"DELIVERY","pending",status,"待确认",1,"ERP-1",
                0L,"",List.of(new Item(11L,2,null,null)),LocalDateTime.now());
    }
    @BeforeEach void setup(){
        erp=mock(TradeErpInventoryService.class);orders=mock(TradeOrderMapper.class);items=mock(TradeOrderItemMapper.class);
        update=new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(update,"erpInventory",erp);
        ReflectionTestUtils.setField(update,"tradeOrderMapper",orders);
        ReflectionTestUtils.setField(update,"tradeOrderItemMapper",items);
        ReflectionTestUtils.setField(update,"tradeOrderHandlers",List.of());
        ReflectionTestUtils.setField(update,"paymentDeadline", new TradeOrderPaymentDeadlineService(
                new cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties(),
                mock(cn.iocoder.txgy.module.pay.api.order.PayOrderApi.class)));
        ReflectionTestUtils.setField(update,"deliveryExpressService",mock(DeliveryExpressService.class));
        order=new TradeOrderDO().setId(1L).setNo("O-1").setStatus(10).setPayStatus(true).setRefundStatus(0).setDeliveryType(1);
        item=new TradeOrderItemDO().setId(2L).setOrderId(1L).setSkuId(11L).setCount(2).setAfterSaleStatus(0);
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        when(orders.selectById(1L)).thenReturn(order);
        when(items.selectListByOrderId(1L)).thenReturn(List.of(item));
    }
    @Test void pendingOutboundDoesNotMarkOrderDelivered(){
        when(erp.request(any(),eq(Operation.OUTBOUND),anyString(),anyList(),any(),any())).thenReturn(entry("OUTBOUND","PENDING"));
        update.deliveryOrder(new TradeOrderDeliveryReqVO().setId(1L).setLogisticsId(0L).setLogisticsNo(""));
        verify(orders,never()).updateByIdAndStatus(any(),any(),any());assertEquals(10,order.getStatus());
    }
    @Test void unpaidOrderCannotRequestOutbound(){
        order.setStatus(0).setPayStatus(false);
        assertThrows(Exception.class,()->update.deliveryOrder(new TradeOrderDeliveryReqVO().setId(1L).setLogisticsId(0L)));
        verifyNoInteractions(erp);
    }
    @Test void afterSaleOrderCannotRequestOutbound(){
        item.setAfterSaleStatus(10);
        assertThrows(Exception.class,()->update.deliveryOrder(new TradeOrderDeliveryReqVO().setId(1L).setLogisticsId(0L)));
        verifyNoInteractions(erp);
    }
    @Test void reservePendingDoesNotCreatePayment(){
        order.setStatus(0).setPayPrice(100);
        when(erp.paymentReady(1L)).thenReturn(false);
        update.continueAfterErpReservation(1L);
        verify(orders,never()).updateById(any(TradeOrderDO.class));
    }
    @Test void canceledOrderIsNotReopenedByReservationContinuation(){
        order.setStatus(40);update.continueAfterErpReservation(1L);verifyNoInteractions(erp);
    }
    @Test void lifecycleHandlerUsesErpAndRefundIsNotPhysicalReturn(){
        var handler=new TradeProductSkuOrderHandler();
        ReflectionTestUtils.setField(handler,"erpInventory",erp);
        handler.afterOrderCreate(order,List.of(item));
        verify(erp).request(order,Operation.RESERVE,"ORDER",List.of(item),null,null);
        handler.afterCancelOrderItem(order,item);
        verify(erp).request(order,Operation.RELEASE,"ITEM-2",List.of(item),null,null);
        reset(erp);order.setStatus(20).setDeliveryTime(LocalDateTime.now());
        handler.afterCancelOrderItem(order,item);verifyNoInteractions(erp);
    }
    @Test void callbackAuthenticatesBeforeReadingRequests(){
        var results=new TradeErpResultService(erp,mock(TradeOrderQueryService.class),mock(TradeOrderUpdateService.class),
                mock(AfterSaleService.class),mock(AfterSaleMapper.class));
        doThrow(new IllegalStateException("denied")).when(erp).authenticateCallback("wrong");
        assertThrows(IllegalStateException.class,()->results.receive("wrong",new Callback("req-1","CONFIRMED","ERP-1","确认")));
        verify(erp,never()).get(anyString());
    }
    @Test void outboundCallbackUsesNormalDeliveryFlowAndDuplicateIsIgnored(){
        var query=mock(TradeOrderQueryService.class);var updates=mock(TradeOrderUpdateService.class);
        var result=new TradeErpResultService(erp,query,updates,mock(AfterSaleService.class),mock(AfterSaleMapper.class));
        when(query.getOrder(1L)).thenReturn(order);
        when(erp.get("req-1")).thenReturn(entry("OUTBOUND","PENDING"));
        when(erp.confirm(any())).thenReturn(entry("OUTBOUND","CONFIRMED"));
        result.receive("secret",new Callback("req-1","CONFIRMED","ERP-1","确认"));
        verify(updates).deliveryOrder(argThat(v->v.getId()==1L&&v.getLogisticsId()==0L));
        reset(updates);when(erp.get("req-1")).thenReturn(entry("OUTBOUND","CONFIRMED"));
        result.receive("secret",new Callback("req-1","CONFIRMED","ERP-1","确认"));verifyNoInteractions(updates);
    }
    @Test void lateReservationForCanceledOrderRequestsRelease(){
        var query=mock(TradeOrderQueryService.class);var updates=mock(TradeOrderUpdateService.class);
        var result=new TradeErpResultService(erp,query,updates,mock(AfterSaleService.class),mock(AfterSaleMapper.class));
        order.setStatus(40);when(query.getOrder(1L)).thenReturn(order);when(query.getOrderItemListByOrderId(1L)).thenReturn(List.of(item));
        when(erp.get("req-1")).thenReturn(entry("RESERVE","PENDING"));when(erp.confirm(any())).thenReturn(entry("RESERVE","CONFIRMED"));
        result.receive("secret",new Callback("req-1","CONFIRMED","ERP-1","确认"));
        verify(erp).request(order,Operation.RELEASE,"LATE-req-1",List.of(item),null,null);verifyNoInteractions(updates);
    }
    @Test void returnCallbackOnlyAdvancesMatchingReturnedAfterSale(){
        var query=mock(TradeOrderQueryService.class);var sales=mock(AfterSaleService.class);var mapper=mock(AfterSaleMapper.class);
        var result=new TradeErpResultService(erp,query,mock(TradeOrderUpdateService.class),sales,mapper);
        when(query.getOrder(1L)).thenReturn(order);
        when(erp.get("req-1")).thenReturn(entry("RETURN","PENDING"));when(erp.confirm(any())).thenReturn(entry("RETURN","CONFIRMED"));
        when(mapper.selectById(5L)).thenReturn(new AfterSaleDO().setId(5L).setOrderId(1L).setStatus(30));
        result.receive("secret",new Callback("req-1","CONFIRMED","ERP-1","确认"));verify(sales).receiveAfterSale(null,5L);
    }
}
