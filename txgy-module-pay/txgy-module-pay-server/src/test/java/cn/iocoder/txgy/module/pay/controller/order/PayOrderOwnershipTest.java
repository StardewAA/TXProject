package cn.iocoder.txgy.module.pay.controller.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.pay.controller.admin.order.PayOrderController;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.txgy.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO;
import cn.iocoder.txgy.module.pay.controller.app.order.AppPayOrderController;
import cn.iocoder.txgy.module.pay.controller.app.order.vo.AppPayOrderSubmitReqVO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.wallet.PayWalletDO;
import cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.wallet.WalletPayClient;
import cn.iocoder.txgy.module.pay.service.app.PayAppService;
import cn.iocoder.txgy.module.pay.service.order.PayOrderService;
import cn.iocoder.txgy.module.pay.service.wallet.PayWalletService;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderOwnershipTest {
    @InjectMocks private AppPayOrderController app;
    @InjectMocks private PayOrderController admin;
    @Mock private PayOrderService orders;
    @Mock private PayWalletService wallets;
    @Mock private PayAppService applications;

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    private void login(long id, int type) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(id).setUserType(type), new MockHttpServletRequest());
    }

    private PayOrderDO order(Long owner, Integer type) {
        return new PayOrderDO().setId(7L).setStatus(0).setUserId(owner).setUserType(type);
    }

    private AppPayOrderSubmitReqVO request(Long id, String channel) {
        AppPayOrderSubmitReqVO value = new AppPayOrderSubmitReqVO();
        value.setId(id).setChannelCode(channel);
        return value;
    }

    private void noPaymentOrWallet() {
        verify(orders, never()).submitOrder(any(), any());
        verify(orders, never()).syncOrderQuietly(anyLong());
        verifyNoInteractions(wallets);
    }

    @Test void anonymousGetAndSubmitRejectedBeforeDatabaseOrWallet() {
        assertThrows(ServiceException.class, () -> app.getOrder(7L, null, true));
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        verifyNoInteractions(orders, wallets);
    }

    @Test void unauthenticatedPrincipalIsNotAValidMemberSession() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(316L).setUserType(1), null));
        assertThrows(ServiceException.class, () -> app.getOrder(7L, null, true));
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        verifyNoInteractions(orders, wallets);
    }

    @Test void adminWithSameNumericIdCannotUseMemberEndpoint() {
        login(316L, 2);
        assertThrows(ServiceException.class, () -> app.getOrder(7L, null, true));
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        verifyNoInteractions(orders, wallets);
    }

    @Test void anotherMembersOrderNeverSyncsOrCreatesWallet() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(317L, 1));
        assertNull(app.getOrder(7L, null, true).getCheckedData());
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        noPaymentOrWallet();
    }

    @Test void sameNumericIdButAdminOwnedOrderIsNotMemberOwned() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(316L, 2));
        assertNull(app.getOrder(7L, null, true).getCheckedData());
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        noPaymentOrWallet();
    }

    @Test void legacyMissingOwnerOrTypeIsNotPublicOrPayable() {
        login(316L, 1);
        for (PayOrderDO legacy : new PayOrderDO[]{order(null, 1), order(316L, null), order(null, null)}) {
            when(orders.getOrder(7L)).thenReturn(legacy);
            assertNull(app.getOrder(7L, null, true).getCheckedData());
            assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        }
        noPaymentOrWallet();
    }

    @Test void missingOrderDoesNotCreateWallet() {
        login(316L, 1);
        assertNull(app.getOrder(7L, null, true).getCheckedData());
        assertThrows(ServiceException.class, () -> app.submitPayOrder(request(7L, "wallet")));
        noPaymentOrWallet();
    }

    @Test void lookupByPaymentNumberDoesNotBypassOwnerCheck() {
        login(316L, 1);
        when(orders.getOrder("attempt-9")).thenReturn(order(317L, 1));
        assertNull(app.getOrder(null, "attempt-9", true).getCheckedData());
        noPaymentOrWallet();
    }

    @Test void invalidIdsRejectedBeforeOrderLookup() {
        login(316L, 1);
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThrows(ServiceException.class, () -> app.submitPayOrder(request(id, "wallet")));
            assertThrows(ServiceException.class, () -> admin.submitPayOrder(request(id, "wallet")));
            assertThrows(ServiceException.class, () -> app.getOrder(id, null, true));
        }
        verifyNoInteractions(orders, wallets);
    }

    @Test void legitimateMemberMayReadAndSynchronizeOwnOrder() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(316L, 1));
        assertEquals(7L, app.getOrder(7L, null, true).getCheckedData().getId());
        verify(orders).syncOrderQuietly(7L);
        verifyNoInteractions(wallets);
    }

    @Test void synchronizationResponseIsCheckedAgainBeforeReturningIt() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(316L, 1), order(317L, 1));
        assertNull(app.getOrder(7L, null, true).getCheckedData());
        verify(orders).syncOrderQuietly(7L);
        verifyNoInteractions(wallets);
    }

    @Test void ownWalletPaymentUsesAuthenticatedWalletNotCallerSuppliedWalletId() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(316L, 1));
        when(wallets.getOrCreateWallet(316L, 1)).thenReturn(new PayWalletDO().setId(500L));
        when(orders.submitOrder(any(), any())).thenReturn(new PayOrderSubmitRespVO().setStatus(0));
        AppPayOrderSubmitReqVO request = request(7L, "wallet");
        request.setChannelExtras(new HashMap<>());
        request.getChannelExtras().put(WalletPayClient.WALLET_ID_KEY, "another-wallet");
        assertNotNull(app.submitPayOrder(request).getCheckedData());
        var sequence = inOrder(orders, wallets);
        sequence.verify(orders).getOrder(7L);
        sequence.verify(wallets).getOrCreateWallet(316L, 1);
        sequence.verify(orders).submitOrder(same(request), any());
        assertEquals("500", request.getChannelExtras().get(WalletPayClient.WALLET_ID_KEY));
    }

    @Test void ownNonWalletPaymentKeepsExistingChannelFlow() {
        login(316L, 1);
        when(orders.getOrder(7L)).thenReturn(order(316L, 1));
        when(orders.submitOrder(any(), any())).thenReturn(new PayOrderSubmitRespVO().setStatus(0));
        assertNotNull(app.submitPayOrder(request(7L, "alipay_wap")).getCheckedData());
        verifyNoInteractions(wallets);
    }

    @Test void adminEndpointCannotBeUsedToPayAnyMemberOrder() {
        login(316L, 2);
        when(orders.getOrder(7L)).thenReturn(order(316L, 1));
        assertThrows(ServiceException.class, () -> admin.submitPayOrder(request(7L, "wallet")));
        noPaymentOrWallet();
    }

    @Test void adminDemoOrdersRemainCompatible() {
        login(316L, 2);
        when(orders.getOrder(7L)).thenReturn(order(316L, 2));
        when(orders.submitOrder(any(), any())).thenReturn(new PayOrderSubmitRespVO().setStatus(0));
        assertNotNull(admin.submitPayOrder(request(7L, "alipay_wap")).getCheckedData());
        verifyNoInteractions(wallets);
    }

    @Test void missingAdminOrderCannotCreateWallet() {
        login(316L, 2);
        assertThrows(ServiceException.class, () -> admin.submitPayOrder(request(7L, "wallet")));
        noPaymentOrWallet();
    }

    @Test void httpPayloadValidationIsActiveAndRequiresPositiveIdAndChannel() throws Exception {
        assertTrue(AppPayOrderController.class.getMethod("submitPayOrder", AppPayOrderSubmitReqVO.class)
                .getParameters()[0].isAnnotationPresent(Valid.class));
        assertTrue(PayOrderController.class.getMethod("submitPayOrder", PayOrderSubmitReqVO.class)
                .getParameters()[0].isAnnotationPresent(Valid.class));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Long id : new Long[]{null, 0L, -1L}) assertFalse(validator.validate(request(id, "wallet")).isEmpty());
            assertFalse(validator.validate(request(7L, "")).isEmpty());
            assertTrue(validator.validate(request(7L, "wallet")).isEmpty());
        }
    }
}
