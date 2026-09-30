package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpInquiryRespVO {
    private Long id;
    private String no;
    private Long purchaseRequestId;
    private String purchaseRequestNo;
    private Integer status;
    private String processInstanceId;
    @Schema(description = "询价反审批原因")
    private String reverseApproveReason;
    private Integer businessStatus;
    private Integer awardStatus;
    private String awardProcessInstanceId;
    @Schema(description = "定标反审批原因")
    private String awardReverseApproveReason;
    private String title;
    private Integer inquiryMode;
    private LocalDateTime deadline;
    private LocalDateTime deliveryDate;
    private String deliveryAddress;
    private String currency;
    private String paymentTerm;
    private String fileUrl;
    private String remark;
    private BigDecimal totalCount;
    @Schema(description = "询价金额：取最低报价合计（最优报价），无报价时为空")
    private BigDecimal totalPrice;
    private Integer quoteCount;
    private String purchaseOrderIds;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
    private String productNames;
    private List<Item> items;
    private List<Quote> quotes;
    /** 受邀供应商名单；为空表示不限受邀范围 */
    private List<InvitedSupplier> invitedSuppliers;

    @Data
    public static class InvitedSupplier {
        private Long supplierId;
        private String supplierName;
        /** 报价状态：0-未报价，20-已报价 */
        private Integer quoteStatus;
        private LocalDateTime invitedAt;
    }

    @Data
    public static class Item {
        private Long id;
        private Long purchaseRequestItemId;
        private Long productId;
        private String productName;
        private String productBarCode;
        private Long productUnitId;
        private String productUnitName;
        private BigDecimal count;
        private String qualityRequirement;
        private String remark;
    }

    @Data
    public static class Quote {
        private Long id;
        private String no;
        private Long inquiryId;
        private Long supplierId;
        private String supplierName;
        private Integer status;
        private Integer version;
        private LocalDateTime submittedAt;
        private LocalDateTime deliveryDate;
        private String paymentTerm;
        private BigDecimal totalPrice;
        private String fileUrl;
        private String remark;
        private List<QuoteItem> items;
    }

    @Data
    public static class QuoteItem {
        private Long id;
        private Long quoteId;
        private Long inquiryItemId;
        private Long productId;
        private String productName;
        private Long productUnitId;
        private String productUnitName;
        private BigDecimal count;
        private BigDecimal productPrice;
        private BigDecimal taxPercent;
        private BigDecimal taxPrice;
        private BigDecimal freightPrice;
        private BigDecimal totalPrice;
        private Boolean awarded;
        private BigDecimal awardCount;
        private String awardReason;
        private String remark;
    }
}
