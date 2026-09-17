package cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract;

import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmBusinessParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmContactParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmContractTemplateTypeParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.CrmCustomerParseFunction;
import cn.iocoder.txgy.module.crm.framework.operatelog.core.SysAdminUserParseFunction;
import com.mzt.logapi.starter.annotation.DiffLogField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - CRM 合同创建/更新 Request VO")
@Data
public class CrmContractSaveReqVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10430")
    private Long id;

    @Schema(description = "合同名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "王五")
    @DiffLogField(name = "合同名称")
    @NotNull(message = "合同名称不能为空")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "18336")
    @DiffLogField(name = "客户", function = CrmCustomerParseFunction.NAME)
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "商机编号", example = "10864")
    @DiffLogField(name = "商机", function = CrmBusinessParseFunction.NAME)
    private Long businessId;

    @Schema(description = "负责人的用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "17144")
    @DiffLogField(name = "负责人", function = SysAdminUserParseFunction.NAME)
    @NotNull(message = "负责人不能为空")
    private Long ownerUserId;

    @Schema(description = "下单日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @DiffLogField(name = "下单日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @NotNull(message = "下单日期不能为空")
    private LocalDateTime orderDate;

    @Schema(description = "开始时间")
    @DiffLogField(name = "开始时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @DiffLogField(name = "结束时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

    @Schema(description = "整单折扣", requiredMode = Schema.RequiredMode.REQUIRED, example = "55.00")
    @DiffLogField(name = "整单折扣")
    @NotNull(message = "整单折扣不能为空")
    private BigDecimal discountPercent;

    @Schema(description = "合同金额", example = "5617")
    @DiffLogField(name = "合同金额")
    private BigDecimal totalPrice;

    @Schema(description = "客户签约人编号", example = "18546")
    @DiffLogField(name = "客户签约人", function = CrmContactParseFunction.NAME)
    private Long signContactId;

    @Schema(description = "公司签约人", example = "14036")
    @DiffLogField(name = "公司签约人", function = SysAdminUserParseFunction.NAME)
    private Long signUserId;

    @Schema(description = "备注", example = "你猜")
    @DiffLogField(name = "备注")
    private String remark;

    // ==================== 合同模板相关字段 ====================

    @Schema(description = "合同模板类型，1=采购合同（我方为甲方），2=购销合同（我方为乙方）", example = "1")
    @DiffLogField(name = "合同模板类型", function = CrmContractTemplateTypeParseFunction.NAME)
    private Integer templateType;

    @Schema(description = "签订地点", example = "成都市")
    @DiffLogField(name = "签订地点")
    private String signPlace;

    @Schema(description = "签订日期")
    @DiffLogField(name = "签订日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime signDate;

    @Schema(description = "项目编号", example = "SCZC-2026-001")
    @DiffLogField(name = "项目编号")
    private String projectNo;

    @Schema(description = "采购项目名称/建设项目名称", example = "森林资源保护能力提升建设项目")
    @DiffLogField(name = "项目名称")
    private String projectName;

    @Schema(description = "地名，仅购销合同使用", example = "汶川")
    @DiffLogField(name = "地名")
    private String projectArea;

    @Schema(description = "附件份数，仅购销合同使用", example = "2")
    @DiffLogField(name = "附件份数")
    private Integer attachmentCopies;

    @Schema(description = "附件页数，仅购销合同使用", example = "10")
    @DiffLogField(name = "附件页数")
    private Integer attachmentPages;

    @Schema(description = "合同份数", example = "4")
    @DiffLogField(name = "合同份数")
    private Integer contractCopies;

    @Schema(description = "政府采购中心名称", example = "四川省")
    @DiffLogField(name = "政府采购中心")
    private String gpcName;

    // ---------- 质量、交货与验收 ----------
    @Schema(description = "国家（行业）标准", example = "国家")
    @DiffLogField(name = "质量标准")
    private String qualityStandard;

    @Schema(description = "送样天数", example = "5")
    @DiffLogField(name = "送样天数")
    private Integer sampleDays;

    @Schema(description = "交货期限天数", example = "30")
    @DiffLogField(name = "交货期限天数")
    private Integer deliveryDays;

    @Schema(description = "交货到指定地点天数", example = "30")
    @DiffLogField(name = "到货天数")
    private Integer deliveryArriveDays;

    @Schema(description = "安装调试验收天数", example = "10")
    @DiffLogField(name = "安装调试验收天数")
    private Integer installDays;

    @Schema(description = "最迟完成安装调试验收日期")
    @DiffLogField(name = "最迟交付日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime deliveryDeadline;

    @Schema(description = "初步验收天数", example = "7")
    @DiffLogField(name = "初步验收天数")
    private Integer firstAcceptDays;

    @Schema(description = "试用期天数", example = "30")
    @DiffLogField(name = "试用期天数")
    private Integer trialDays;

    @Schema(description = "最终验收天数", example = "7")
    @DiffLogField(name = "最终验收天数")
    private Integer finalAcceptDays;

    @Schema(description = "甲方不验收视同合格天数", example = "15")
    @DiffLogField(name = "视同验收合格天数")
    private Integer autoAcceptDays;

    @Schema(description = "维修次数上限（达到可退货）", example = "3")
    @DiffLogField(name = "维修次数上限")
    private Integer maxRepairTimes;

    // ---------- 付款方式 ----------
    @Schema(description = "首付款金额", example = "10000.00")
    @DiffLogField(name = "首付款金额")
    private BigDecimal prepayAmount;

    @Schema(description = "首付支付天数", example = "15")
    @DiffLogField(name = "首付支付天数")
    private Integer prepayDays;

    @Schema(description = "首付款比例(%)", example = "30.00")
    @DiffLogField(name = "首付款比例")
    private BigDecimal prepayRatio;

    @Schema(description = "验收合格后付款天数（购销为工作日）", example = "15")
    @DiffLogField(name = "验收款支付天数")
    private Integer acceptPayDays;

    @Schema(description = "验收款比例(%)", example = "60.00")
    @DiffLogField(name = "验收款比例")
    private BigDecimal acceptPayRatio;

    @Schema(description = "验收款金额", example = "20000.00")
    @DiffLogField(name = "验收款金额")
    private BigDecimal acceptPayAmount;

    @Schema(description = "履约保证金月数", example = "12")
    @DiffLogField(name = "保证金月数")
    private Integer depositMonths;

    @Schema(description = "保证金退还天数", example = "15")
    @DiffLogField(name = "保证金退还天数")
    private Integer depositDays;

    @Schema(description = "保证金金额", example = "5000.00")
    @DiffLogField(name = "保证金金额")
    private BigDecimal depositAmount;

    @Schema(description = "保证金比例(%)", example = "10.00")
    @DiffLogField(name = "保证金比例")
    private BigDecimal depositRatio;

    // ---------- 售后服务 ----------
    @Schema(description = "质保年数", example = "1")
    @DiffLogField(name = "质保年数")
    private Integer warrantyYears;

    @Schema(description = "响应到场小时数", example = "24")
    @DiffLogField(name = "响应小时数")
    private Integer responseHours;

    @Schema(description = "完成维修小时数", example = "48")
    @DiffLogField(name = "维修小时数")
    private Integer repairHours;

    @Schema(description = "质保期内维修次数上限", example = "3")
    @DiffLogField(name = "维修次数")
    private Integer repairTimes;

    @Schema(description = "报修无法解决提供代用品天数", example = "3")
    @DiffLogField(name = "报修天数")
    private Integer reportRepairDays;

    // ---------- 甲方（采购人）主体信息 ----------
    @Schema(description = "甲方单位名称", example = "某某林业和园林局")
    @DiffLogField(name = "甲方名称")
    private String partyAName;
    @Schema(description = "甲方法定代表人", example = "张三")
    @DiffLogField(name = "甲方法定代表人")
    private String partyALegalPerson;
    @Schema(description = "甲方授权代表/授权委托人", example = "李四")
    @DiffLogField(name = "甲方授权代表")
    private String partyAAgent;
    @Schema(description = "甲方地址", example = "成都市xx路1号")
    @DiffLogField(name = "甲方地址")
    private String partyAAddress;
    @Schema(description = "甲方开户银行", example = "中国工商银行xx支行")
    @DiffLogField(name = "甲方开户银行")
    private String partyABankName;
    @Schema(description = "甲方银行账号", example = "1234567890")
    @DiffLogField(name = "甲方账号")
    private String partyABankAccount;
    @Schema(description = "甲方电话", example = "028-88888888")
    @DiffLogField(name = "甲方电话")
    private String partyATel;
    @Schema(description = "甲方传真", example = "028-88888889")
    @DiffLogField(name = "甲方传真")
    private String partyAFax;

    // ---------- 乙方（供应商）主体信息 ----------
    @Schema(description = "乙方单位名称", example = "天信防腐保温有限公司")
    @DiffLogField(name = "乙方名称")
    private String partyBName;
    @Schema(description = "乙方法定代表人", example = "王五")
    @DiffLogField(name = "乙方法定代表人")
    private String partyBLegalPerson;
    @Schema(description = "乙方授权代表/授权委托人", example = "赵六")
    @DiffLogField(name = "乙方授权代表")
    private String partyBAgent;
    @Schema(description = "乙方地址", example = "成都市xx路2号")
    @DiffLogField(name = "乙方地址")
    private String partyBAddress;
    @Schema(description = "乙方开户银行", example = "中国建设银行xx支行")
    @DiffLogField(name = "乙方开户银行")
    private String partyBBankName;
    @Schema(description = "乙方银行账号", example = "0987654321")
    @DiffLogField(name = "乙方账号")
    private String partyBBankAccount;
    @Schema(description = "乙方电话", example = "028-66666666")
    @DiffLogField(name = "乙方电话")
    private String partyBTel;
    @Schema(description = "乙方传真", example = "028-66666667")
    @DiffLogField(name = "乙方传真")
    private String partyBFax;

    @Schema(description = "违约责任条款（JSON，默认取合同配置）")
    @DiffLogField(name = "违约责任条款")
    private String penaltyTerms;

    @Schema(description = "产品列表")
    private List<Product> products;

    @Schema(description = "产品列表")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {

        @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20529")
        @NotNull(message = "产品编号不能为空")
        private Long productId;

        @Schema(description = "产品单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        @NotNull(message = "产品单价不能为空")
        private BigDecimal productPrice;

        @Schema(description = "合同价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        @NotNull(message = "合同价格不能为空")
        private BigDecimal contractPrice;

        @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
        @NotNull(message = "产品数量不能为空")
        private Integer count;

        // ---------- 合同模板「一、合同货物」表格字段 ----------
        @Schema(description = "规格型号", example = "DN200")
        private String spec;

        @Schema(description = "单位", example = "台")
        private String unit;

        @Schema(description = "资金来源：1=预算内，2=预算外，3=其他", example = "1")
        private Integer fundSource;

        @Schema(description = "随机配件", example = "说明书、保修卡")
        private String accessories;

        @Schema(description = "交货期", example = "合同签订后30日内")
        private String deliveryPeriod;

    }

}
