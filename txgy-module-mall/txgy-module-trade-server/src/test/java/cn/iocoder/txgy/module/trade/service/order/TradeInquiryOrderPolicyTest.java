package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.inquiry.ProductInquiryApi;
import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeInquiryOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderUpdateAddressReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.mockito.Mockito.*;

class TradeInquiryOrderPolicyTest {

    private TradeOrderMapper orderMapper;
    private ProductInquiryApi productInquiryApi;
    private TradeOrderUpdateServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        orderMapper = mock(TradeOrderMapper.class);
        productInquiryApi = mock(ProductInquiryApi.class);
        service = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(service, "tradeOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "productInquiryApi", productInquiryApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void canceledInquiryOrderCannotBeDeletedAndLoseItsIdempotencyRecord() {
        TradeOrderDO order = new TradeOrderDO().setId(11L).setUserId(7L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .setStatus(TradeOrderStatusEnum.CANCELED.getStatus());
        when(orderMapper.selectOrderByIdAndUserId(11L, 7L)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteOrder(7L, 11L));

        assertEquals(1_011_000_050, error.getCode());
        verify(orderMapper, never()).deleteById(anyLong());
    }

    @Test
    void canceledNormalOrderKeepsExistingDeleteBehavior() {
        TradeOrderDO order = new TradeOrderDO().setId(12L).setUserId(7L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_NORMAL)
                .setStatus(TradeOrderStatusEnum.CANCELED.getStatus());
        when(orderMapper.selectOrderByIdAndUserId(12L, 7L)).thenReturn(order);
        try (var logs = mockStatic(TradeOrderLogUtils.class)) {
            service.deleteOrder(7L, 12L);
        }
        verify(orderMapper).deleteById(12L);
    }

    @Test
    void retryAfterInquiryCancellationReturnsTheOriginalOrder() {
        TradeOrderDO canceled = new TradeOrderDO().setId(13L).setUserId(7L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .setInquiryQuoteId(31L).setBuyerEnterpriseId(71L)
                .setStatus(TradeOrderStatusEnum.CANCELED.getStatus());
        InquiryAcceptedQuoteRespDTO quote = validQuote(7L, 71L, 31L);
        when(productInquiryApi.getAcceptedQuote(7L, 31L)).thenReturn(success(quote));
        when(orderMapper.selectByInquiryQuoteId(31L)).thenReturn(canceled);
        AppTradeInquiryOrderCreateReqVO request = new AppTradeInquiryOrderCreateReqVO();
        request.setQuoteId(31L);
        request.setAddressId(9L);
        request.setRequestKey("retry-canceled-inquiry");

        assertSame(canceled, service.createInquiryOrder(7L, request));
        verify(orderMapper, never()).insert(any(TradeOrderDO.class));
    }

    @Test
    void anotherBuyerInSameEnterpriseCanRecoverTheOriginalOrder() {
        TradeOrderDO existing = new TradeOrderDO().setId(14L).setUserId(7L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .setInquiryQuoteId(31L).setBuyerEnterpriseId(71L);
        when(productInquiryApi.getAcceptedQuote(8L, 31L)).thenReturn(success(validQuote(8L, 71L, 31L)));
        when(orderMapper.selectByInquiryQuoteId(31L)).thenReturn(existing);

        AppTradeInquiryOrderCreateReqVO request = new AppTradeInquiryOrderCreateReqVO()
                .setQuoteId(31L).setAddressId(9L).setRequestKey("same-enterprise-retry");

        assertSame(existing, service.createInquiryOrder(8L, request));
    }

    @Test
    void orderFromAnotherEnterpriseIsRejected() {
        TradeOrderDO existing = new TradeOrderDO().setId(15L).setUserId(7L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY)
                .setInquiryQuoteId(31L).setBuyerEnterpriseId(99L);
        when(productInquiryApi.getAcceptedQuote(8L, 31L)).thenReturn(success(validQuote(8L, 71L, 31L)));
        when(orderMapper.selectByInquiryQuoteId(31L)).thenReturn(existing);

        AppTradeInquiryOrderCreateReqVO request = new AppTradeInquiryOrderCreateReqVO()
                .setQuoteId(31L).setAddressId(9L).setRequestKey("cross-enterprise-retry");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createInquiryOrder(8L, request));
        assertEquals(1_011_000_052, error.getCode());
    }

    @Test
    void enterpriseAddressEntryCannotTouchNormalOrAnotherEnterpriseOrder() {
        TradeOrderDO normal = new TradeOrderDO().setId(41L).setUserId(8L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_NORMAL).setBuyerEnterpriseId(71L);
        TradeOrderDO foreign = new TradeOrderDO().setId(42L).setUserId(8L)
                .setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY).setBuyerEnterpriseId(72L);
        when(orderMapper.selectByIdForUpdate(41L)).thenReturn(normal);
        when(orderMapper.selectByIdForUpdate(42L)).thenReturn(foreign);

        assertThrows(ServiceException.class, () -> service.updateInquiryOrderAddressByEnterprise(9L, 71L,
                new AppTradeOrderUpdateAddressReqVO().setId(41L).setAddressId(1L)));
        assertThrows(ServiceException.class, () -> service.updateInquiryOrderAddressByEnterprise(9L, 71L,
                new AppTradeOrderUpdateAddressReqVO().setId(42L).setAddressId(1L)));

        verify(orderMapper, never()).updateEnterpriseInquiryUnpaidAddressAndPrice(any(), anyLong());
    }

    private static InquiryAcceptedQuoteRespDTO validQuote(Long memberId, Long enterpriseId, Long quoteId) {
        InquiryAcceptedQuoteRespDTO.Item item = new InquiryAcceptedQuoteRespDTO.Item()
                .setInquiryItemId(101L).setSkuId(1001L).setCount(1).setUnitPrice(100);
        return new InquiryAcceptedQuoteRespDTO().setQuoteId(quoteId).setInquiryId(21L)
                .setBuyerEnterpriseId(enterpriseId).setBuyerMemberId(memberId)
                .setShopId(31L).setVersion(1L).setItems(java.util.List.of(item));
    }
}
