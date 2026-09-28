package cn.iocoder.txgy.module.trade.service.workbench;

import jakarta.validation.constraints.*;
import java.util.*;

public final class OrderWorkbenchModels {
    private OrderWorkbenchModels() {}
    public enum Role { BUYER, SUPPLIER, ADMIN }
    public enum Action { PAYMENT, RECEIVE, COMMENT, BUYER_RETURN, DELIVERY, AFTER_AUDIT, AFTER_RECEIVE, PLATFORM_REFUND, ERP_EXCEPTION }
    public record Scope(long tenant, int userType, long user, Role role, long shop, Set<Action> actions) {
        public String key() { return tenant+":"+userType+":"+user+":"+role+":"+shop; }
        public long syncId() { return role==Role.BUYER ? user : role==Role.SUPPLIER ? shop : 0; }
    }
    public record Entry(String eventId, String entityType, long entityId, long orderId, Long afterSaleId,
                        String requestId, String no, long shopId, Action action, String reason,
                        boolean unread, String createdTime) {}
    public record Counts(long allCount, long unpaidCount, long undeliveredCount, long deliveredCount,
                         long completedCount, long canceledCount, long uncommentedCount) {}
    public record Summary(String scopeKey, long serverTime, String cursor, boolean resetRequired,
                          int pollIntervalSeconds, long pendingCount, long unreadCount, long newCount,
                          Map<Action,Long> todoCounts, Counts orderStatusCounts, List<Entry> entries,
                          boolean hasMore, List<Entry> rowEntries) {}
    public record Read(@NotNull Role role, Long shopId,
                       @NotEmpty @Size(max=100) List<@Pattern(regexp="[1-9][0-9]{0,18}") String> eventIds) {}
    record Candidate(String key, Role role, long member, long shop, long order, String type,
                     long entity, Long afterSale, String request, String no, Action action, String reason) {}
}
