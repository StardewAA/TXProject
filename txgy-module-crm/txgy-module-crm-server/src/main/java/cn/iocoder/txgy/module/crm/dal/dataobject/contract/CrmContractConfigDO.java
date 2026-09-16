package cn.iocoder.txgy.module.crm.dal.dataobject.contract;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;

@TableName("crm_contract_config")
@KeySequence("crm_contract_config_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmContractConfigDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 是否开启提前提醒
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean notifyEnabled;
    /**
     * 提前提醒天数
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer notifyDays;

    // ==================== 我方主体信息 ====================
    // 创建合同时自动带入到对应方（采购合同→甲方，购销合同→乙方），允许单份合同覆盖
    private String ourCompanyName;
    private String ourLegalPerson;
    private String ourAgent;
    private String ourAddress;
    private String ourBankName;
    private String ourBankAccount;
    private String ourTel;
    private String ourFax;

    // ==================== 条款默认值 ====================
    // 新增合同时，若对应字段未填写，则使用下面的默认值自动带入

    // ---------- 通用 ----------
    private String defaultQualityStandard;
    private Integer defaultSampleDays;
    private Integer defaultDeliveryDays;
    private Integer defaultDeliveryArriveDays;
    private Integer defaultInstallDays;
    private Integer defaultFirstAcceptDays;
    private Integer defaultTrialDays;
    private Integer defaultFinalAcceptDays;
    private Integer defaultAutoAcceptDays;
    private Integer defaultMaxRepairTimes;
    private BigDecimal defaultPrepayRatio;
    private Integer defaultPrepayDays;
    private BigDecimal defaultAcceptPayRatio;
    private Integer defaultAcceptPayDays;
    private BigDecimal defaultDepositRatio;
    private Integer defaultDepositMonths;
    private Integer defaultDepositDays;
    private Integer defaultWarrantyYears;
    private Integer defaultResponseHours;
    private Integer defaultRepairHours;
    private Integer defaultRepairTimes;
    private Integer defaultReportRepairDays;
    private Integer defaultContractCopies;
    private String defaultGpcName;

    // ---------- 违约责任默认值：采购合同 ----------
    private BigDecimal pBuyerRejectRatio;
    private BigDecimal pBuyerOverdueWan;
    private Integer pBuyerOverdueDays;
    private BigDecimal pSellerQuality;
    private BigDecimal pSellerOverdueWan;
    private Integer pSellerOverdueDays;
    private BigDecimal pSellerCompensate;
    private Integer pSellerReplaceDays;
    private BigDecimal pSellerTerminate;
    private BigDecimal pSellerDefect;

    // ---------- 违约责任默认值：购销合同 ----------
    private BigDecimal sBuyerLateRatio;
    private BigDecimal sBuyerBreachRatio;
    private BigDecimal sSellerLateRatio;
    private BigDecimal sSellerBreachRatio;

}
