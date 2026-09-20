package cn.iocoder.txgy.framework.common.util.audit;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkTraceTest {
    @Test void redactsNestedSecretsIncludingJsonEncodedBodies() {
        String input = "{\"body\":\"{\\\"Password\\\":\\\"secret123\\\",\\\"name\\\":\\\"钢管\\\"}\",\"rows\":[{\"access_token\":\"token123\",\"quantity\":3}],\"Authorization\":\"Bearer xyz\"}";
        String result = WorkTrace.sanitizeJson(input);
        assertFalse(result.contains("secret123"));
        assertFalse(result.contains("token123"));
        assertFalse(result.contains("Bearer xyz"));
        assertTrue(result.contains("钢管"));
        assertTrue(result.contains("quantity"));
    }
    @Test void invalidJsonIsNotStoredAsPotentialSecretPlaintext() {
        assertFalse(WorkTrace.sanitizeJson("password=supersecret").contains("supersecret"));
    }
    @Test void clearsThreadLocalBetweenRequests() {
        WorkTrace.begin();
        WorkTrace.add("wms_stock", "UPDATE", List.of(Map.of("id",1,"quantity",2)), List.of(Map.of("id",1,"quantity",3)));
        assertTrue(WorkTrace.snapshot().contains("quantity"));
        WorkTrace.clear();
        WorkTrace.begin();
        assertFalse(WorkTrace.snapshot().contains("quantity"));
        WorkTrace.clear();
        assertFalse(WorkTrace.active());
    }
}
