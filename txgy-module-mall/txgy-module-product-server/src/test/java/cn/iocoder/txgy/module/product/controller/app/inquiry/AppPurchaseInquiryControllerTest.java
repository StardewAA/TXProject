package cn.iocoder.txgy.module.product.controller.app.inquiry;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import jakarta.annotation.security.PermitAll;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppPurchaseInquiryControllerTest {

    @Mock
    private PurchaseInquiryService service;
    @Mock
    private PurchaseInquiryMarketService marketService;
    @InjectMocks
    private AppPurchaseInquiryController controller;

    @Test
    void publicReadEndpointsRemainAnonymousAndTenantIndependent() throws Exception {
        var publicPage = AppPurchaseInquiryController.class.getDeclaredMethod(
                "publicPage", PageParam.class, String.class, String.class);
        var publicGet = AppPurchaseInquiryController.class.getDeclaredMethod("publicGet", Long.class);

        assertNotNull(publicPage.getAnnotation(PermitAll.class));
        assertNotNull(publicPage.getAnnotation(TenantIgnore.class));
        assertNotNull(publicGet.getAnnotation(PermitAll.class));
        assertNotNull(publicGet.getAnnotation(TenantIgnore.class));

        var save = AppPurchaseInquiryController.class.getDeclaredMethod("save", InquirySaveReqVO.class);
        assertNull(save.getAnnotation(PermitAll.class));
        assertNull(save.getAnnotation(TenantIgnore.class));
    }

    @Test
    void publicPageDelegatesToMarketService() {
        PageParam page = new PageParam();
        page.setPageNo(2);
        page.setPageSize(12);
        PageResult<InquiryPublicSummaryRespVO> expected = new PageResult<>(List.of(), 0L);
        when(marketService.publicPage(page, "open", "保温")).thenReturn(expected);

        assertSame(expected, controller.publicPage(page, "open", "保温").getData());
        verify(marketService).publicPage(page, "open", "保温");
    }

    @Test
    void publicGetDelegatesToMarketService() {
        InquiryPublicDetailRespVO expected = new InquiryPublicDetailRespVO();
        when(marketService.publicGet(10L)).thenReturn(expected);

        assertSame(expected, controller.publicGet(10L).getData());
        verify(marketService).publicGet(10L);
    }
}
