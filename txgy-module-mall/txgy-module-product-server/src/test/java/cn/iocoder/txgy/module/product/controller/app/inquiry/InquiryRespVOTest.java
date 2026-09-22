package cn.iocoder.txgy.module.product.controller.app.inquiry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class InquiryRespVOTest {

    @Test
    void newPurchaseUiContractDoesNotExposeLegacyErpFields() {
        var names = Arrays.stream(InquiryRespVO.class.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList();
        assertFalse(names.contains("erpStatus"));
        assertFalse(names.contains("erpNo"));
    }
}
