package cn.iocoder.txgy.module.crm.controller.admin.contract.vo.config;

import cn.hutool.core.util.BooleanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mzt.logapi.starter.annotation.DiffLogField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Objects;

@Schema(description = "管理后台 - CRM 合同配置 Request VO")
@Data
public class CrmContractConfigSaveReqVO {

    @Schema(description = "是否开启提前提醒", example = "true")
    @DiffLogField(name = "是否开启提前提醒")
    private Boolean notifyEnabled;

    @Schema(description = "提前提醒天数", example = "2")
    @DiffLogField(name = "提前提醒天数")
    private Integer notifyDays;

    // ==================== 我方主体信息 ====================
    @Schema(description = "我方公司名称", example = "天信防腐保温有限公司")
    @DiffLogField(name = "我方公司名称")
    private String ourCompanyName;
    @Schema(description = "我方法定代表人", example = "张三")
    @DiffLogField(name = "我方法定代表人")
    private String ourLegalPerson;
    @Schema(description = "我方授权代表", example = "李四")
    @DiffLogField(name = "我方授权代表")
    private String ourAgent;
    @Schema(description = "我方地址", example = "成都市xx路1号")
    @DiffLogField(name = "我方地址")
    private String ourAddress;
    @Schema(description = "我方开户银行", example = "中国工商银行xx支行")
    @DiffLogField(name = "我方开户银行")
    private String ourBankName;
    @Schema(description = "我方银行账号", example = "1234567890")
    @DiffLogField(name = "我方银行账号")
    private String ourBankAccount;
    @Schema(description = "我方电话", example = "028-88888888")
    @DiffLogField(name = "我方电话")
    private String ourTel;
    @Schema(description = "我方传真", example = "028-88888889")
    @DiffLogField(name = "我方传真")
    private String ourFax;

    // ==================== 条款默认值 ====================
    @Schema(description = "默认国家（行业）标准", example = "国家")
    @DiffLogField(name = "默认质量标准")
    private String defaultQualityStandard;
    @Schema(description = "默认送样天数", example = "5")
    @DiffLogField(name = "默认送样天数")
    private Integer defaultSampleDays;
    @Schema(description = "默认交货期限天数", example = "30")
    @DiffLogField(name = "默认交货期限天数")
    private Integer defaultDeliveryDays;
    @Schema(description = "默认到货天数", example = "30")
    @DiffLogField(name = "默认到货天数")
    private Integer defaultDeliveryArriveDays;
    @Schema(description = "默认安装调试验收天数", example = "10")
    @DiffLogField(name = "默认安装调试验收天数")
    private Integer defaultInstallDays;
    @Schema(description = "默认初步验收天数", example = "7")
    @DiffLogField(name = "默认初步验收天数")
    private Integer defaultFirstAcceptDays;
    @Schema(description = "默认试用期天数", example = "30")
    @DiffLogField(name = "默认试用期天数")
    private Integer defaultTrialDays;
    @Schema(description = "默认最终验收天数", example = "7")
    @DiffLogField(name = "默认最终验收天数")
    private Integer defaultFinalAcceptDays;
    @Schema(description = "默认视同验收合格天数", example = "15")
    @DiffLogField(name = "默认视同验收合格天数")
    private Integer defaultAutoAcceptDays;
    @Schema(description = "默认维修次数上限", example = "3")
    @DiffLogField(name = "默认维修次数上限")
    private Integer defaultMaxRepairTimes;
    @Schema(description = "默认首付款比例(%)", example = "30.00")
    @DiffLogField(name = "默认首付款比例")
    private BigDecimal defaultPrepayRatio;
    @Schema(description = "默认首付支付天数", example = "15")
    @DiffLogField(name = "默认首付支付天数")
    private Integer defaultPrepayDays;
    @Schema(description = "默认验收款比例(%)", example = "60.00")
    @DiffLogField(name = "默认验收款比例")
    private BigDecimal defaultAcceptPayRatio;
    @Schema(description = "默认验收款支付天数", example = "15")
    @DiffLogField(name = "默认验收款支付天数")
    private Integer defaultAcceptPayDays;
    @Schema(description = "默认保证金比例(%)", example = "10.00")
    @DiffLogField(name = "默认保证金比例")
    private BigDecimal defaultDepositRatio;
    @Schema(description = "默认保证金月数", example = "12")
    @DiffLogField(name = "默认保证金月数")
    private Integer defaultDepositMonths;
    @Schema(description = "默认保证金退还天数", example = "15")
    @DiffLogField(name = "默认保证金退还天数")
    private Integer defaultDepositDays;
    @Schema(description = "默认质保年数", example = "1")
    @DiffLogField(name = "默认质保年数")
    private Integer defaultWarrantyYears;
    @Schema(description = "默认响应小时数", example = "24")
    @DiffLogField(name = "默认响应小时数")
    private Integer defaultResponseHours;
    @Schema(description = "默认维修小时数", example = "48")
    @DiffLogField(name = "默认维修小时数")
    private Integer defaultRepairHours;
    @Schema(description = "默认维修次数", example = "3")
    @DiffLogField(name = "默认维修次数")
    private Integer defaultRepairTimes;
    @Schema(description = "默认报修天数", example = "3")
    @DiffLogField(name = "默认报修天数")
    private Integer defaultReportRepairDays;
    @Schema(description = "默认合同份数", example = "4")
    @DiffLogField(name = "默认合同份数")
    private Integer defaultContractCopies;
    @Schema(description = "默认政府采购中心名称", example = "四川省")
    @DiffLogField(name = "默认政府采购中心")
    private String defaultGpcName;

    // ---------- 违约责任默认值：采购合同 ----------
    @Schema(description = "甲方拒收货物违约金比例(%)", example = "5.00")
    @DiffLogField(name = "甲方拒收违约金比例")
    private BigDecimal pBuyerRejectRatio;
    @Schema(description = "甲方逾期付款违约金(万分之/天)", example = "5.00")
    @DiffLogField(name = "甲方逾期付款违约金")
    private BigDecimal pBuyerOverdueWan;
    @Schema(description = "甲方逾期付款可终止合同天数", example = "30")
    @DiffLogField(name = "甲方逾期终止天数")
    private Integer pBuyerOverdueDays;
    @Schema(description = "乙方质量不合格违约金比例(%)", example = "5.00")
    @DiffLogField(name = "乙方质量不合格违约金比例")
    private BigDecimal pSellerQuality;
    @Schema(description = "乙方逾期交货违约金(万分之/天)", example = "5.00")
    @DiffLogField(name = "乙方逾期交货违约金")
    private BigDecimal pSellerOverdueWan;
    @Schema(description = "乙方逾期交货可终止合同天数", example = "30")
    @DiffLogField(name = "乙方逾期终止天数")
    private Integer pSellerOverdueDays;
    @Schema(description = "乙方终止合同赔偿金比例(%)", example = "10.00")
    @DiffLogField(name = "乙方终止赔偿比例")
    private BigDecimal pSellerCompensate;
    @Schema(description = "检测不合格无条件更换天数", example = "15")
    @DiffLogField(name = "检测不合格更换天数")
    private Integer pSellerReplaceDays;
    @Schema(description = "逾期不能更换赔偿金比例(%)", example = "10.00")
    @DiffLogField(name = "逾期未更换赔偿比例")
    private BigDecimal pSellerTerminate;
    @Schema(description = "权利瑕疵违约金比例(%)", example = "10.00")
    @DiffLogField(name = "权利瑕疵违约金比例")
    private BigDecimal pSellerDefect;

    // ---------- 违约责任默认值：购销合同 ----------
    @Schema(description = "甲方逾期付款滞纳金比例(%/日)", example = "0.50")
    @DiffLogField(name = "甲方逾期滞纳金比例")
    private BigDecimal sBuyerLateRatio;
    @Schema(description = "甲方违约金比例(%)", example = "5.00")
    @DiffLogField(name = "甲方违约金比例")
    private BigDecimal sBuyerBreachRatio;
    @Schema(description = "乙方逾期供货滞纳金比例(%/日)", example = "0.50")
    @DiffLogField(name = "乙方逾期滞纳金比例")
    private BigDecimal sSellerLateRatio;
    @Schema(description = "乙方违约金比例(%)", example = "5.00")
    @DiffLogField(name = "乙方违约金比例")
    private BigDecimal sSellerBreachRatio;

    @AssertTrue(message = "提前提醒天数不能为空")
    @JsonIgnore
    public boolean isNotifyDaysValid() {
        if (!BooleanUtil.isTrue(getNotifyEnabled())) {
            return true;
        }
        return Objects.nonNull(getNotifyDays());
    }

}
