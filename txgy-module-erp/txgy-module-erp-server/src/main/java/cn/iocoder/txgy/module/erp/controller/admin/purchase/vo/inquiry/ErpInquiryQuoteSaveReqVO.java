package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpInquiryQuoteSaveReqVO {
    private Long id;
    @NotNull(message = "询价单不能为空")
    private Long inquiryId;
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    private Integer version;
    private LocalDateTime deliveryDate;
    private String paymentTerm;
    private String fileUrl;
    private String remark;
    @Valid
    @NotEmpty(message = "报价明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        @NotNull(message = "询价明细不能为空")
        private Long inquiryItemId;
        @NotNull(message = "产品不能为空")
        private Long productId;
        @NotNull(message = "单位不能为空")
        private Long productUnitId;
        @NotNull(message = "报价数量不能为空")
        private BigDecimal count;
        @NotNull(message = "未税单价不能为空")
        private BigDecimal productPrice;
        private BigDecimal taxPercent;
        private BigDecimal freightPrice;
        private String remark;
    }
}
