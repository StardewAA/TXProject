package cn.iocoder.txgy.module.crm.controller.admin.contract.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - CRM 合同配置 Response VO")
@Data
public class CrmContractConfigRespVO {

    @Schema(description = "是否开启提前提醒", example = "true")
    private Boolean notifyEnabled;

    @Schema(description = "提前提醒天数", example = "2")
    private Integer notifyDays;

    // ==================== 我方主体信息 ====================
    @Schema(description = "我方公司名称", example = "天信防腐保温有限公司")
    private String ourCompanyName;
    @Schema(description = "我方法定代表人", example = "张三")
    private String ourLegalPerson;
    @Schema(description = "我方授权代表", example = "李四")
    private String ourAgent;
    @Schema(description = "我方地址", example = "成都市xx路1号")
    private String ourAddress;
    @Schema(description = "我方开户银行", example = "中国工商银行xx支行")
    private String ourBankName;
    @Schema(description = "我方银行账号", example = "1234567890")
    private String ourBankAccount;
    @Schema(description = "我方电话", example = "028-88888888")
    private String ourTel;
    @Schema(description = "我方传真", example = "028-88888889")
    private String ourFax;

    // ==================== 条款默认值 ====================
    @Schema(description = "默认国家（行业）标准", example = "国家")
    private String defaultQualityStandard;
    @Schema(description = "默认送样天数", example = "5")
    private Integer defaultSampleDays;
    @Schema(description = "默认交货期限天数", example = "30")
    private Integer defaultDeliveryDays;
    @Schema(description = "默认到货天数", example = "30")
    private Integer defaultDeliveryArriveDays;
    @Schema(description = "默认安装调试验收天数", example = "10")
    private Integer defaultInstallDays;
    @Schema(description = "默认初步验收天数", example = "7")
    private Integer defaultFirstAcceptDays;
    @Schema(description = "默认试用期天数", example = "30")
    private Integer defaultTrialDays;
    @Schema(description = "默认最终验收天数", example = "7")
    private Integer defaultFinalAcceptDays;
    @Schema(description = "默认视同验收合格天数", example = "15")
    private Integer defaultAutoAcceptDays;
    @Schema(description = "默认维修次数上限", example = "3")
    private Integer defaultMaxRepairTimes;
    @Schema(description = "默认首付款比例(%)", example = "30.00")
    private BigDecimal defaultPrepayRatio;
    @Schema(description = "默认首付支付天数", example = "15")
    private Integer defaultPrepayDays;
    @Schema(description = "默认验收款比例(%)", example = "60.00")
    private BigDecimal defaultAcceptPayRatio;
    @Schema(description = "默认验收款支付天数", example = "15")
    private Integer defaultAcceptPayDays;
    @Schema(description = "默认保证金比例(%)", example = "10.00")
    private BigDecimal defaultDepositRatio;
    @Schema(description = "默认保证金月数", example = "12")
    private Integer defaultDepositMonths;
    @Schema(description = "默认保证金退还天数", example = "15")
    private Integer defaultDepositDays;
    @Schema(description = "默认质保年数", example = "1")
    private Integer defaultWarrantyYears;
    @Schema(description = "默认响应小时数", example = "24")
    private Integer defaultResponseHours;
    @Schema(description = "默认维修小时数", example = "48")
    private Integer defaultRepairHours;
    @Schema(description = "默认维修次数", example = "3")
    private Integer defaultRepairTimes;
    @Schema(description = "默认报修天数", example = "3")
    private Integer defaultReportRepairDays;
    @Schema(description = "默认合同份数", example = "4")
    private Integer defaultContractCopies;
    @Schema(description = "默认政府采购中心名称", example = "四川省")
    private String defaultGpcName;

    // ---------- 违约责任默认值：采购合同 ----------
    @Schema(description = "甲方拒收货物违约金比例(%)", example = "5.00")
    private BigDecimal pBuyerRejectRatio;
    @Schema(description = "甲方逾期付款违约金(万分之/天)", example = "5.00")
    private BigDecimal pBuyerOverdueWan;
    @Schema(description = "甲方逾期付款可终止合同天数", example = "30")
    private Integer pBuyerOverdueDays;
    @Schema(description = "乙方质量不合格违约金比例(%)", example = "5.00")
    private BigDecimal pSellerQuality;
    @Schema(description = "乙方逾期交货违约金(万分之/天)", example = "5.00")
    private BigDecimal pSellerOverdueWan;
    @Schema(description = "乙方逾期交货可终止合同天数", example = "30")
    private Integer pSellerOverdueDays;
    @Schema(description = "乙方终止合同赔偿金比例(%)", example = "10.00")
    private BigDecimal pSellerCompensate;
    @Schema(description = "检测不合格无条件更换天数", example = "15")
    private Integer pSellerReplaceDays;
    @Schema(description = "逾期不能更换赔偿金比例(%)", example = "10.00")
    private BigDecimal pSellerTerminate;
    @Schema(description = "权利瑕疵违约金比例(%)", example = "10.00")
    private BigDecimal pSellerDefect;

    // ---------- 违约责任默认值：购销合同 ----------
    @Schema(description = "甲方逾期付款滞纳金比例(%/日)", example = "0.50")
    private BigDecimal sBuyerLateRatio;
    @Schema(description = "甲方违约金比例(%)", example = "5.00")
    private BigDecimal sBuyerBreachRatio;
    @Schema(description = "乙方逾期供货滞纳金比例(%/日)", example = "0.50")
    private BigDecimal sSellerLateRatio;
    @Schema(description = "乙方违约金比例(%)", example = "5.00")
    private BigDecimal sSellerBreachRatio;

}
