package cn.iocoder.txgy.module.trade.service.wms;

import cn.iocoder.txgy.module.wms.api.integration.WmsUnitCodeDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsUnitCodeDictionaryTest {

    @Test
    void normalizesProjectAliasesWithoutDoingPackagingConversion() {
        assertEquals("M2", WmsUnitCodeDictionary.resolve("㎡").code());
        assertEquals("M2", WmsUnitCodeDictionary.resolve("m²").code());
        assertEquals("KG", WmsUnitCodeDictionary.resolve("公斤").code());
        assertEquals("PC", WmsUnitCodeDictionary.resolve("件").code());
        assertEquals("PC", WmsUnitCodeDictionary.resolve("个").code());
        assertEquals("BUCKET", WmsUnitCodeDictionary.resolve("桶").code());
        assertNull(WmsUnitCodeDictionary.resolve("EA"));
        assertNull(WmsUnitCodeDictionary.resolve("DRUM"));
        assertNotEquals(WmsUnitCodeDictionary.resolve("箱").code(), WmsUnitCodeDictionary.resolve("个").code());
        assertNull(WmsUnitCodeDictionary.resolve("呃呃"));
        assertEquals(31, WmsUnitCodeDictionary.all().size());
    }
}
