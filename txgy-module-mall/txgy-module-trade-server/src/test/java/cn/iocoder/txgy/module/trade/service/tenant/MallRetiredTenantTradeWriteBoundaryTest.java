package cn.iocoder.txgy.module.trade.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.admin.delivery.vo.expresstemplate.DeliveryExpressTemplateCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.delivery.vo.expresstemplate.DeliveryExpressTemplateUpdateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppCartAddReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeInquiryOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.service.cart.CartServiceImpl;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressTemplateServiceImpl;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.RETIRED_MALL_TENANT_WRITE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MallRetiredTenantTradeWriteBoundaryTest {

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(3000L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void blocksCartAndOrderCreationBeforeDependenciesAreUsed() {
        assertBlocked(() -> new CartServiceImpl().addCart(1L, new AppCartAddReqVO()));
        TradeOrderUpdateServiceImpl orders = new TradeOrderUpdateServiceImpl();
        assertBlocked(() -> orders.createOrder(1L, new AppTradeOrderCreateReqVO()));
        assertBlocked(() -> orders.createInquiryOrder(1L, new AppTradeInquiryOrderCreateReqVO()));
    }

    @Test
    void blocksExpressTemplateWritesBeforeDependenciesAreUsed() {
        DeliveryExpressTemplateServiceImpl templates = new DeliveryExpressTemplateServiceImpl();
        assertBlocked(() -> templates.createDeliveryExpressTemplate(new DeliveryExpressTemplateCreateReqVO()));
        assertBlocked(() -> templates.updateDeliveryExpressTemplate(new DeliveryExpressTemplateUpdateReqVO()));
        assertBlocked(() -> templates.deleteDeliveryExpressTemplate(1L));
    }

    private static void assertBlocked(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(RETIRED_MALL_TENANT_WRITE_FORBIDDEN.getCode(), error.getCode());
    }
}
