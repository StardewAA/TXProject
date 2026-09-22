package cn.iocoder.txgy.module.trade.controller.app.checkout;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutFormalizationPrepareReqVO;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutFormalizationService;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppPlatformCheckoutControllerTest {

    private final PlatformCheckoutService checkoutService = mock(PlatformCheckoutService.class);
    private final PlatformCheckoutFormalizationService formalizationService =
            mock(PlatformCheckoutFormalizationService.class);
    private final AppPlatformCheckoutController controller =
            new AppPlatformCheckoutController(checkoutService, formalizationService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prepareUsesOnlyLegacyMemberAndSourceTenantFromSession() {
        LoginUser loginUser = new LoginUser().setId(316L).setTenantId(2009L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        var preparation = new PlatformCheckoutFormalizationService.Preparation(
                40L, "pc202609211200001", "a".repeat(64), false,
                List.of(PlatformCheckoutFormalizationService.Blocker.FEATURE_DISABLED,
                        PlatformCheckoutFormalizationService.Blocker.AGGREGATE_PAYMENT_UNAVAILABLE),
                "正式子订单功能开关默认关闭；聚合支付及子订单资金分摊尚未接通");
        when(formalizationService.prepare(316L, 2009L, "pc202609211200001", "formalize-01"))
                .thenReturn(preparation);

        var response = controller.prepareFormalization("pc202609211200001",
                new AppPlatformCheckoutFormalizationPrepareReqVO().setIdempotencyKey("formalize-01")).getData();

        assertEquals(40L, response.getAttemptId());
        assertEquals("pc202609211200001", response.getCheckoutNo());
        assertFalse(response.getReady());
        assertEquals(List.of("FEATURE_DISABLED", "AGGREGATE_PAYMENT_UNAVAILABLE"),
                response.getBlockerCodes());
        verify(formalizationService).prepare(316L, 2009L, "pc202609211200001", "formalize-01");
        verifyNoInteractions(checkoutService);
    }

    @Test
    void anonymousCannotPrepareFormalization() {
        SecurityContextHolder.clearContext();

        assertThrows(IllegalStateException.class, () -> controller.prepareFormalization(
                "pc202609211200001",
                new AppPlatformCheckoutFormalizationPrepareReqVO().setIdempotencyKey("formalize-01")));

        verifyNoInteractions(formalizationService, checkoutService);
    }
}
