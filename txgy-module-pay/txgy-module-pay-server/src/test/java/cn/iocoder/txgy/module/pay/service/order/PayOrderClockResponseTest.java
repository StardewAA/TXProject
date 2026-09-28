package cn.iocoder.txgy.module.pay.service.order;

import cn.iocoder.txgy.module.pay.controller.app.order.AppPayOrderController;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOrderClockResponseTest {
    @InjectMocks private AppPayOrderController controller;
    @Mock private PayOrderService payOrderService;

    @BeforeEach void login() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(316L).setUserType(1), new MockHttpServletRequest());
    }

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void appCashierReceivesServerClockAndActualPersistedExpiry() {
        LocalDateTime expiry = LocalDateTime.of(2026, 9, 15, 12, 30);
        when(payOrderService.getOrder(7L)).thenReturn(new PayOrderDO().setId(7L).setStatus(0)
                .setExpireTime(expiry).setMerchantOrderId("123").setUserId(316L).setUserType(1));
        long before = System.currentTimeMillis();
        var response = controller.getOrder(7L, null, false).getCheckedData();
        assertEquals(expiry, response.getExpireTime());
        assertEquals(expiry.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), response.getExpireTimestamp());
        assertTrue(response.getServerTime() >= before && response.getServerTime() <= System.currentTimeMillis());
        assertEquals("123", response.getMerchantOrderId());
    }

    @Test void legacyMissingExpiryIsNotInvented() {
        when(payOrderService.getOrder(7L)).thenReturn(new PayOrderDO().setId(7L).setStatus(0).setUserId(316L).setUserType(1));
        var response = controller.getOrder(7L, null, false).getCheckedData();
        assertNotNull(response.getServerTime());
        assertNull(response.getExpireTimestamp());
    }
}
