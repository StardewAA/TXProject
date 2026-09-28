package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpInquirySaveReqVO {
    private Long id;
    private Long purchaseRequestId;
    @NotBlank(message = "询价标题不能为空")
    private String title;
    @NotNull(message = "询价方式不能为空")
    private Integer inquiryMode;
    @NotNull(message = "报价截止时间不能为空")
    private LocalDateTime deadline;
    private LocalDateTime deliveryDate;
    private String deliveryAddress;
    private String currency;
    private String paymentTerm;
    private String fileUrl;
    private String remark;
    /**
     * 受邀供应商编号列表。
     *
     * <p>为空表示「不限受邀范围」（历史行为：所有供应商都可报价），
     * 非空时只有名单内的供应商能报价、也只有他们能在供应商视角看到该询价单。</p>
     */
    private List<Long> supplierIds;
    @Valid
    @NotEmpty(message = "询价明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Long purchaseRequestItemId;
        @NotNull(message = "产品不能为空")
        private Long productId;
        @NotNull(message = "产品单位不能为空")
        private Long productUnitId;
        @NotNull(message = "询价数量不能为空")
        private BigDecimal count;
        private String qualityRequirement;
        private String remark;
    }
}
