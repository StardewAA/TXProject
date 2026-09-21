package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpLogisticsDetailRespVO extends ErpLogisticsPageRespVO {

    private String requestId;
    private String businessChainNo;
    private String carrierCode;
    private String vehicleNo;
    private String driverName;
    private String driverPhoneMasked;
    private LocalDateTime plannedDepartureTime;
    private String resumeStatus;
    private Long latestEventVersion;
    private String latestNodeCode;
    private String trackUrl;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime createTime;
    private List<Item> items;
    private List<Node> nodes;
    private List<Receipt> receipts;
    private List<ExceptionRecord> exceptions;
    private List<FulfillmentEvent> fulfillmentEvents;

    @Data
    public static class Item {
        private Long id;
        private Integer lineNo;
        private String sourceItemNo;
        private Long productId;
        private String materialCode;
        private String itemNameSnapshot;
        private String specificationSnapshot;
        private String unitNameSnapshot;
        private BigDecimal quantity;
    }

    @Data
    public static class Node {
        private Long id;
        private String nodeCode;
        private String nodeName;
        private LocalDateTime occurredAt;
        private LocalDateTime receivedAt;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private String locationDescription;
        private String remark;
        private String reportSource;
        private Long eventVersion;
        private List<ErpLogisticsAttachmentVO> attachments;
    }

    @Data
    public static class Receipt {
        private Long id;
        private String receiptType;
        private String receiverName;
        private LocalDateTime receiptTime;
        private BigDecimal receiptQuantity;
        private String remark;
        private Long eventVersion;
        private List<ErpLogisticsAttachmentVO> attachments;
    }

    @Data
    public static class ExceptionRecord {
        private Long id;
        private Long nodeId;
        private String exceptionCode;
        private String exceptionName;
        private String description;
        private String status;
        private LocalDateTime occurredAt;
        private Long handlerUserId;
        private String handleResult;
        private LocalDateTime handleTime;
    }

    @Data
    public static class FulfillmentEvent {
        private Long id;
        private String eventCode;
        private String eventName;
        private String eventDescription;
        private LocalDateTime occurredAt;
        private Long eventVersion;
    }

}
