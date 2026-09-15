package cn.iocoder.txgy.framework.search;

import cn.iocoder.txgy.framework.search.core.SearchRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {

    @Test
    void rejectsMissingTenantOrUnboundedQuery() {
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest(0, "劳动合同", 0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest(1, " ", 0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest(1, "法".repeat(201), 0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest(1, "劳动合同", -1, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest(1, "劳动合同", 0, 101, null));
    }

    @Test
    void copiesFiltersWithoutMutatingCallerState() {
        Map<String, String> filters = new HashMap<>();
        filters.put("region", "610000");
        SearchRequest request = new SearchRequest(1, "劳动合同", 0, 10, filters);
        filters.put("region", "other");
        assertEquals("610000", request.filters().get("region"));
        assertThrows(UnsupportedOperationException.class, () -> request.filters().put("status", "draft"));
    }

}
