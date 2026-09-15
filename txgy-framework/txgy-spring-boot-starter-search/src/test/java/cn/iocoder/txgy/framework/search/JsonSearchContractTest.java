package cn.iocoder.txgy.framework.search;

import cn.iocoder.txgy.framework.search.core.JsonSearchDocument;
import cn.iocoder.txgy.framework.search.core.JsonSearchRequest;
import cn.iocoder.txgy.framework.search.core.JsonSearchRequest.NumericRange;
import cn.iocoder.txgy.framework.search.core.JsonSearchRequest.TagFilter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSearchContractTest {
    @Test
    void allowsBoundedBrowsingButRejectsInvalidScope() {
        assertEquals("", new JsonSearchRequest("material", 1, null, 0, 10, null, null).keyword());
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSearchRequest("material", 0, "", 0, 10, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSearchRequest("material:*", 1, "", 0, 10, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSearchRequest("material", 1, "", 0, 101, null, null));
    }

    @Test
    void preservesTypedTemperatureBounds() {
        NumericRange lower = new NumericRange("temp_min", null, new BigDecimal("-40"));
        NumericRange upper = new NumericRange("temp_max", new BigDecimal("100"), null);
        JsonSearchRequest request = new JsonSearchRequest("material", 1, "", 0, 10, null,
                List.of(lower, upper));
        assertNull(request.ranges().getFirst().minimum());
        assertEquals(new BigDecimal("-40"), request.ranges().getFirst().maximum());
        assertEquals(new BigDecimal("100"), request.ranges().getLast().minimum());
        assertThrows(IllegalArgumentException.class,
                () -> new NumericRange("temp_min", BigDecimal.TEN, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new NumericRange("price", null, null));
    }

    @Test
    void protectsPermissionFieldsAndCopiesTags() {
        List<String> values = new ArrayList<>(List.of("钢结构"));
        TagFilter filter = new TagFilter("applicable", values);
        values.add("管道");
        assertEquals(List.of("钢结构"), filter.values());
        assertThrows(UnsupportedOperationException.class, () -> filter.values().add("屋面"));
        for (String field : List.of("tenant_id", "publish_status", "visibility")) {
            assertThrows(IllegalArgumentException.class, () -> new TagFilter(field, List.of("x")));
            assertThrows(IllegalArgumentException.class, () -> new NumericRange(field, BigDecimal.ONE, null));
        }
    }

    @Test
    void keepsNumericJsonValuesAndRejectsIdentityOverrides() {
        JsonSearchDocument document = new JsonSearchDocument("material", 1, "mat001", 2,
                Map.of("temp_min", new BigDecimal("-40"), "applicable", List.of("钢结构"),
                        "updated_at", 1704067200000L));
        assertInstanceOf(BigDecimal.class, document.fields().get("temp_min"));
        assertInstanceOf(Long.class, document.fields().get("updated_at"));
        assertThrows(IllegalArgumentException.class, () -> new JsonSearchDocument(
                "material", 1, "mat001", 2, Map.of("tenant_id", 2)));
        assertThrows(IllegalArgumentException.class, () -> new JsonSearchDocument(
                "material", 1, "mat001", 0, Map.of()));
    }
}
