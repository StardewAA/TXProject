package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP采购合同新增/修改 Request VO")
@Data
public class ErpPurchaseContractSaveReqVO {
    private Long id;
    @NotBlank(message = "合同名称不能为空")
    private String contractName;
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "请选择已审批采购订单")
    private Long purchaseOrderId;
    /**
     * 关联采购订单号
     */
    private String purchaseOrderNo;
    private LocalDateTime signDate;
    private LocalDateTime effectiveDate;
    private LocalDateTime expireDate;
    private BigDecimal totalAmount;
    private BigDecimal taxRate;
    private String paymentTerms;
    private String deliveryPlace;
    private LocalDateTime deliveryDeadline;
    private String signPlace;
    private String currency;
    private Long signerId;
    private String qualityStandard;
    @Min(value = 0, message = "验收期限不能小于 0")
    private Integer acceptancePeriodDays;
    @Min(value = 0, message = "质保期限不能小于 0")
    private Integer warrantyPeriodMonths;
    @Min(value = 0, message = "售后响应时限不能小于 0")
    private Integer afterSaleResponseHours;
    @Min(value = 0, message = "到场服务时限不能小于 0")
    private Integer onSiteServiceHours;
    @Min(value = 0, message = "最大维修次数不能小于 0")
    private Integer maxRepairTimes;
    @DecimalMin(value = "0", message = "预付款比例不能小于 0")
    @DecimalMax(value = "100", message = "预付款比例不能大于 100")
    private BigDecimal advancePaymentRatio;
    @Min(value = 0, message = "预付款期限不能小于 0")
    private Integer advancePaymentDays;
    @DecimalMin(value = "0", message = "验收款比例不能小于 0")
    @DecimalMax(value = "100", message = "验收款比例不能大于 100")
    private BigDecimal acceptancePaymentRatio;
    @Min(value = 0, message = "验收款期限不能小于 0")
    private Integer acceptancePaymentDays;
    @DecimalMin(value = "0", message = "质保金比例不能小于 0")
    @DecimalMax(value = "100", message = "质保金比例不能大于 100")
    private BigDecimal warrantyRetentionRatio;
    @Min(value = 0, message = "质保金释放期限不能小于 0")
    private Integer warrantyReleaseDays;
    private String warrantyReleaseCondition;
    @DecimalMin(value = "0", message = "甲方拒收违约金比例不能小于 0")
    private BigDecimal buyerRejectPenaltyRatio;
    @DecimalMin(value = "0", message = "甲方逾期付款率不能小于 0")
    private BigDecimal buyerOverdueRate;
    @Min(value = 0, message = "甲方逾期解约天数不能小于 0")
    private Integer buyerTerminationDays;
    @DecimalMin(value = "0", message = "乙方质量违约金比例不能小于 0")
    private BigDecimal sellerQualityPenaltyRatio;
    @DecimalMin(value = "0", message = "乙方逾期交货率不能小于 0")
    private BigDecimal sellerOverdueRate;
    @Min(value = 0, message = "乙方逾期解约天数不能小于 0")
    private Integer sellerTerminationDays;
    @DecimalMin(value = "0", message = "乙方解约赔偿比例不能小于 0")
    private BigDecimal sellerTerminationCompensationRatio;
    @DecimalMin(value = "0", message = "权利瑕疵违约金比例不能小于 0")
    private BigDecimal ipDefectPenaltyRatio;
    private String buyerName;
    private String buyerLegalRepresentative;
    private String buyerAddress;
    private String buyerBankName;
    private String buyerBankAccount;
    private String buyerPhone;
    private String buyerFax;
    private String supplierNameSnapshot;
    private String sellerLegalRepresentative;
    private String sellerAddress;
    private String sellerBankName;
    private String sellerBankAccount;
    private String sellerPhone;
    private String sellerFax;
    private Long templateId;
    private String templateName;
    private String templateVersion;
    private String legalTerms;
    private String fileUrl;
    private String remark;
}
