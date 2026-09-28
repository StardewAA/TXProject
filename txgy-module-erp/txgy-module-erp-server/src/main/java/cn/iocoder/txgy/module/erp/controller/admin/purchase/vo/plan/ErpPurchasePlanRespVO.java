package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpPurchasePlanRespVO {
    private Long id;
    private String no;
    private String planName;
    private String planPeriod;
    private Long departmentId;
    private Long requesterId;
    private LocalDateTime planTime;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer status;
    @Schema(description = "反审批原因")
    private String reverseApproveReason;
    private BigDecimal totalCount;
    private BigDecimal budgetAmount;
    private String purpose;
    private String fileUrl;
    private String remark;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
    private String productNames;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Long productId;
        private String productName;
        private Long productUnitId;
        private String productUnitName;
        private BigDecimal count;
        private BigDecimal targetPrice;
        private BigDecimal amount;
        private LocalDateTime requiredDate;
        private String remark;
    }
}
