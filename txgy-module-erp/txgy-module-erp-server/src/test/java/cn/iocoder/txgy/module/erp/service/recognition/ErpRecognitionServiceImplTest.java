package cn.iocoder.txgy.module.erp.service.recognition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ErpRecognitionServiceImplTest {

    @Test
    void shouldNormalizeTaxPercentValues() {
        assertEquals(new BigDecimal("13"), ErpRecognitionServiceImpl.normalizeTaxPercent("13"));
        assertEquals(new BigDecimal("13.00"), ErpRecognitionServiceImpl.normalizeTaxPercent("13.00"));
        assertEquals(new BigDecimal("13"), ErpRecognitionServiceImpl.normalizeTaxPercent("13%"));
        assertEquals(new BigDecimal("13"), ErpRecognitionServiceImpl.normalizeTaxPercent("13％"));
        assertEquals(new BigDecimal("13"), ErpRecognitionServiceImpl.normalizeTaxPercent("0.13"));
        assertEquals(new BigDecimal("13"), ErpRecognitionServiceImpl.normalizeTaxPercent(" 0,13 "));
    }

    @Test
    void shouldReturnNullForBlankOrInvalidTaxPercent() {
        assertNull(ErpRecognitionServiceImpl.normalizeTaxPercent(null));
        assertNull(ErpRecognitionServiceImpl.normalizeTaxPercent(" "));
        assertNull(ErpRecognitionServiceImpl.normalizeTaxPercent("税率未知"));
    }

}
