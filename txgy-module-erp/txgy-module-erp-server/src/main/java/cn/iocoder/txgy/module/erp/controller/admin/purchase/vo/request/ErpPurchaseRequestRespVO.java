package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpPurchaseRequestRespVO {
    private Long id;
    private String no;
    private Integer status;
    private String processInstanceId;
    @Schema(description = "反审批原因")
    private String reverseApproveReason;
    private LocalDateTime requestTime;
    private LocalDateTime requiredDate;
    private Long departmentId;
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
        private String productBarCode;
        private Long productUnitId;
        private String productUnitName;
        private BigDecimal count;
        private BigDecimal convertedCount;
        private BigDecimal targetPrice;
        private String remark;
    }
}
