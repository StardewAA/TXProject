package cn.iocoder.txgy.module.trade.controller.app.order.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only logistics view. Transport SIGNED is not buyer receipt or ERP confirmation. */
@Data
public class AppTradeOrderLogisticsRespVO {
    private Long orderId;
    private Integer deliveryType;
    private String queryStatus;
    private String message;
    private String carrierName;
    private String carrierCode;
    private String trackingNo;
    private String transportStatus = "UNKNOWN";
    private String transportStatusLabel = "未知";
    private String source;
    private String rawStatus;
    private LocalDateTime updatedTime;
    private boolean cached;
    private boolean stale;
    private int refreshAfterSeconds;
    private List<Event> events = List.of();

    @Data
    public static class Event {
        private LocalDateTime time;
        private String content;
        private String status = "UNKNOWN";
        private String statusLabel = "状态未提供";
        private String rawStatus;
        private String source;
    }
}
