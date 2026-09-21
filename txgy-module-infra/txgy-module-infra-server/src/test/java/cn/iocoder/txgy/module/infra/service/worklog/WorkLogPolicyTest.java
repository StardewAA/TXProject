package cn.iocoder.txgy.module.infra.service.worklog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkLogPolicyTest {
    @Test void selfIgnoresForgedActorFilterEvenForAdmin() {
        assertEquals(10L, WorkLogPolicy.actor("SELF", 10L, 99L, true));
    }
    @Test void allRequiresPermission() {
        assertThrows(RuntimeException.class, () -> WorkLogPolicy.actor("ALL", 10L, null, false));
    }
    @Test void allCanFilterSingleMember() {
        assertEquals(99L, WorkLogPolicy.actor("ALL", 10L, 99L, true));
        assertNull(WorkLogPolicy.actor("ALL", 10L, null, true));
    }
    @Test void missingIdentityAndUnknownScopeAreDenied() {
        assertThrows(RuntimeException.class, () -> WorkLogPolicy.actor("SELF", null, null, true));
        assertThrows(RuntimeException.class, () -> WorkLogPolicy.actor("OTHER", 10L, null, true));
    }
    @Test void detailCannotCrossUserOrTenantBoundary() {
        assertDoesNotThrow(() -> WorkLogPolicy.checkDetail(1L, 1L, 10L, 10L));
        assertThrows(RuntimeException.class, () -> WorkLogPolicy.checkDetail(1L, 1L, 10L, 99L));
        assertThrows(RuntimeException.class, () -> WorkLogPolicy.checkDetail(1L, 2L, null, 10L));
    }
}
