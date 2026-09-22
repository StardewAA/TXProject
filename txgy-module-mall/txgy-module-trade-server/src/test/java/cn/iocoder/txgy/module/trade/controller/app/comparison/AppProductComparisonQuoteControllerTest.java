package cn.iocoder.txgy.module.trade.controller.app.comparison;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteReqVO;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteRespVO;
import cn.iocoder.txgy.module.trade.service.comparison.TradeProductComparisonQuoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppProductComparisonQuoteControllerTest {
    private final TradeProductComparisonQuoteService quotes = mock(TradeProductComparisonQuoteService.class);
    private final AppProductComparisonQuoteController controller = new AppProductComparisonQuoteController(quotes);
    private final AppProductComparisonQuoteReqVO request = new AppProductComparisonQuoteReqVO()
            .setSkuIds(List.of(201L)).setQuantity(1).setAddressId(91L);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    private void login(Integer userType) {
        var user = new LoginUser().setId(71L).setUserType(userType).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void memberGetsOnlyOwnQuoteWithPrivateNoStoreHeaders() {
        login(UserTypeEnum.MEMBER.getValue());
        var result = new AppProductComparisonQuoteRespVO();
        when(quotes.quote(71L, request)).thenReturn(result);
        assertSame(result, controller.quote(request, response).getData());
        verify(quotes).quote(71L, request);
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
    }

    @Test
    void administratorWithSameNumericIdCannotReadMemberPricesOrAddresses() {
        login(UserTypeEnum.ADMIN.getValue());
        assertEquals(403, assertThrows(ServiceException.class, () -> controller.quote(request, response)).getCode());
        verifyNoInteractions(quotes);
    }

    @Test
    void anonymousCannotReadMemberPricesOrAddresses() {
        SecurityContextHolder.clearContext();
        assertEquals(403, assertThrows(ServiceException.class, () -> controller.quote(request, response)).getCode());
        verifyNoInteractions(quotes);
    }

    @Test
    void missingUserTypeCannotReadMemberPricesOrAddresses() {
        login(null);
        assertEquals(403, assertThrows(ServiceException.class, () -> controller.quote(request, response)).getCode());
        verifyNoInteractions(quotes);
    }

    @Test
    void defaultDisclaimerDescribesWmsRecheckWithoutLegacyErpPendingPrompt() {
        List<String> warnings = new AppProductComparisonQuoteRespVO().getWarnings();
        assertTrue(warnings.stream().anyMatch(message -> message.contains("WMS") && message.contains("重新复核")));
        assertTrue(warnings.stream().noneMatch(message -> message.contains("ERP")));
    }
}
