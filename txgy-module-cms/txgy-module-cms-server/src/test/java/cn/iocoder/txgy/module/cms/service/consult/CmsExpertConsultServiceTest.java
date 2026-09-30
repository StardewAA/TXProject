package cn.iocoder.txgy.module.cms.service.consult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 专家咨询状态常量的轻量回归测试。完整数据库状态机测试在接入支付与 IM 后补齐。
 */
class CmsExpertConsultServiceTest {

    @Test
    void orderStatusValuesRemainStableForExternalIntegration() {
        assertEquals(0, CmsExpertConsultService.WAIT_PAY);
        assertEquals(5, CmsExpertConsultService.COMPLETED);
        assertEquals(9, CmsExpertConsultService.REFUNDING);
        assertEquals(10, CmsExpertConsultService.REFUNDED);
    }
}
