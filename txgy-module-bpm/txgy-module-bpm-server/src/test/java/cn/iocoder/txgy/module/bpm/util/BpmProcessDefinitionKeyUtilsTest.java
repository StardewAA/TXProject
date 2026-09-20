package cn.iocoder.txgy.module.bpm.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmProcessDefinitionKeyUtilsTest {

    @Test
    void parseKeys_shouldTrimRemoveEmptyAndDeduplicate() {
        assertEquals(List.of("wms_receipt_approval", "wms_shipment_approval"),
                BpmProcessDefinitionKeyUtils.parseKeys(
                        " wms_receipt_approval, wms_shipment_approval,wms_receipt_approval, "));
    }

    @Test
    void parseKeys_shouldReturnEmptyForBlankInput() {
        assertTrue(BpmProcessDefinitionKeyUtils.parseKeys(" ").isEmpty());
        assertTrue(BpmProcessDefinitionKeyUtils.parseKeys(null).isEmpty());
    }

}
