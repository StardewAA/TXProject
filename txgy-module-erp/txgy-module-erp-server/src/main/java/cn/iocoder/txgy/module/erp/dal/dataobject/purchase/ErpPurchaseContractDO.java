package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 采购合同 DO。 */
@TableName("erp_purchase_contract")
@KeySequence("erp_purchase_contract_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpPurchaseContractDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private String contractName;
    private Long supplierId;
    /**
     * 关联采购订单编号
     */
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
    /** 质量、验收与质保条款快照 */
    private String qualityStandard;
    private Integer acceptancePeriodDays;
    private Integer warrantyPeriodMonths;
    private Integer afterSaleResponseHours;
    private Integer onSiteServiceHours;
    private Integer maxRepairTimes;
    /** 付款计划 */
    private BigDecimal advancePaymentRatio;
    private Integer advancePaymentDays;
    private BigDecimal acceptancePaymentRatio;
    private Integer acceptancePaymentDays;
    private BigDecimal warrantyRetentionRatio;
    private Integer warrantyReleaseDays;
    private String warrantyReleaseCondition;
    /** 违约责任 */
    private BigDecimal buyerRejectPenaltyRatio;
    private BigDecimal buyerOverdueRate;
    private Integer buyerTerminationDays;
    private BigDecimal sellerQualityPenaltyRatio;
    private BigDecimal sellerOverdueRate;
    private Integer sellerTerminationDays;
    private BigDecimal sellerTerminationCompensationRatio;
    private BigDecimal ipDefectPenaltyRatio;
    /** 甲乙双方签约资料快照 */
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
    /** 合同模板快照 */
    private Long templateId;
    private String templateName;
    private String templateVersion;
    private String legalTerms;
    private Integer status;
    /**
     * 反审批原因
     */
    private String reverseApproveReason;
    private String fileUrl;
    private String remark;
}
