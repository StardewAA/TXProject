package cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CRM 合同 Response VO")
@Data
@ExcelIgnoreUnannotated
public class CrmContractRespVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10430")
    @ExcelProperty("合同编号")
    private Long id;

    @Schema(description = "合同名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "王五")
    @ExcelProperty("合同名称")
    private String name;

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20230101")
    @ExcelProperty("合同编号")
    private String no;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "18336")
    @ExcelProperty("客户编号")
    private Long customerId;
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "18336")
    @ExcelProperty("客户名称")
    private String customerName;

    @Schema(description = "商机编号", example = "10864")
    @ExcelProperty("商机编号")
    private Long businessId;
    @Schema(description = "商机名称", example = "10864")
    @ExcelProperty("商机名称")
    private String businessName;

    @Schema(description = "最后跟进时间")
    @ExcelProperty("最后跟进时间")
    private LocalDateTime contactLastTime;

    @Schema(description = "负责人的用户编号", example = "25682")
    @ExcelProperty("负责人的用户编号")
    private Long ownerUserId;
    @Schema(description = "负责人名字", example = "25682")
    @ExcelProperty("负责人名字")
    private String ownerUserName;
    @Schema(description = "负责人部门")
    @ExcelProperty("负责人部门")
    private String ownerUserDeptName;

    @Schema(description = "工作流编号", example = "1043")
    @ExcelProperty("工作流编号")
    private String processInstanceId;

    @Schema(description = "审批状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @ExcelProperty("审批状态")
    private Integer auditStatus;

    @Schema(description = "下单日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("下单日期")
    private LocalDateTime orderDate;

    @Schema(description = "开始时间")
    @ExcelProperty("开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @ExcelProperty("结束时间")
    private LocalDateTime endTime;

    @Schema(description = "产品总金额", example = "19510")
    @ExcelProperty("产品总金额")
    private BigDecimal totalProductPrice;

    @Schema(description = "整单折扣")
    @ExcelProperty("整单折扣")
    private BigDecimal discountPercent;

    @Schema(description = "合同金额", example = "5617")
    @ExcelProperty("合同金额")
    private BigDecimal totalPrice;

    @Schema(description = "已回款金额", example = "5617")
    @ExcelProperty("已回款金额")
    private BigDecimal totalReceivablePrice;

    @Schema(description = "客户签约人编号", example = "18546")
    private Long signContactId;
    @Schema(description = "客户签约人", example = "小豆")
    @ExcelProperty("客户签约人")
    private String signContactName;

    @Schema(description = "公司签约人", example = "14036")
    private Long signUserId;
    @Schema(description = "公司签约人", example = "小明")
    @ExcelProperty("公司签约人")
    private String signUserName;

    @Schema(description = "备注", example = "你猜")
    @ExcelProperty("备注")
    private String remark;

    // ==================== 合同模板相关字段 ====================

    @Schema(description = "合同模板类型，1=采购合同，2=购销合同", example = "1")
    @ExcelProperty("合同模板类型")
    private Integer templateType;

    @Schema(description = "签订地点", example = "成都市")
    private String signPlace;

    @Schema(description = "签订日期")
    private LocalDateTime signDate;

    @Schema(description = "项目编号", example = "SCZC-2026-001")
    private String projectNo;

    @Schema(description = "采购项目名称/建设项目名称", example = "森林资源保护能力提升建设项目")
    private String projectName;

    @Schema(description = "地名，仅购销合同使用", example = "汶川")
    private String projectArea;

    @Schema(description = "附件份数，仅购销合同使用", example = "2")
    private Integer attachmentCopies;

    @Schema(description = "附件页数，仅购销合同使用", example = "10")
    private Integer attachmentPages;

    @Schema(description = "合同份数", example = "4")
    private Integer contractCopies;

    @Schema(description = "政府采购中心名称", example = "四川省")
    private String gpcName;

    // ---------- 质量、交货与验收 ----------
    @Schema(description = "国家（行业）标准", example = "国家")
    private String qualityStandard;
    @Schema(description = "送样天数", example = "5")
    private Integer sampleDays;
    @Schema(description = "交货期限天数", example = "30")
    private Integer deliveryDays;
    @Schema(description = "交货到指定地点天数", example = "30")
    private Integer deliveryArriveDays;
    @Schema(description = "安装调试验收天数", example = "10")
    private Integer installDays;
    @Schema(description = "最迟完成安装调试验收日期")
    private LocalDateTime deliveryDeadline;
    @Schema(description = "初步验收天数", example = "7")
    private Integer firstAcceptDays;
    @Schema(description = "试用期天数", example = "30")
    private Integer trialDays;
    @Schema(description = "最终验收天数", example = "7")
    private Integer finalAcceptDays;
    @Schema(description = "甲方不验收视同合格天数", example = "15")
    private Integer autoAcceptDays;
    @Schema(description = "维修次数上限（达到可退货）", example = "3")
    private Integer maxRepairTimes;

    // ---------- 付款方式 ----------
    @Schema(description = "首付款金额", example = "10000.00")
    private BigDecimal prepayAmount;
    @Schema(description = "首付支付天数", example = "15")
    private Integer prepayDays;
    @Schema(description = "首付款比例(%)", example = "30.00")
    private BigDecimal prepayRatio;
    @Schema(description = "验收合格后付款天数（购销为工作日）", example = "15")
    private Integer acceptPayDays;
    @Schema(description = "验收款比例(%)", example = "60.00")
    private BigDecimal acceptPayRatio;
    @Schema(description = "验收款金额", example = "20000.00")
    private BigDecimal acceptPayAmount;
    @Schema(description = "履约保证金月数", example = "12")
    private Integer depositMonths;
    @Schema(description = "保证金退还天数", example = "15")
    private Integer depositDays;
    @Schema(description = "保证金金额", example = "5000.00")
    private BigDecimal depositAmount;
    @Schema(description = "保证金比例(%)", example = "10.00")
    private BigDecimal depositRatio;

    // ---------- 售后服务 ----------
    @Schema(description = "质保年数", example = "1")
    private Integer warrantyYears;
    @Schema(description = "响应到场小时数", example = "24")
    private Integer responseHours;
    @Schema(description = "完成维修小时数", example = "48")
    private Integer repairHours;
    @Schema(description = "质保期内维修次数上限", example = "3")
    private Integer repairTimes;
    @Schema(description = "报修无法解决提供代用品天数", example = "3")
    private Integer reportRepairDays;

    // ---------- 甲方（采购人）主体信息 ----------
    @Schema(description = "甲方单位名称", example = "某某林业和园林局")
    private String partyAName;
    @Schema(description = "甲方法定代表人", example = "张三")
    private String partyALegalPerson;
    @Schema(description = "甲方授权代表/授权委托人", example = "李四")
    private String partyAAgent;
    @Schema(description = "甲方地址", example = "成都市xx路1号")
    private String partyAAddress;
    @Schema(description = "甲方开户银行", example = "中国工商银行xx支行")
    private String partyABankName;
    @Schema(description = "甲方银行账号", example = "1234567890")
    private String partyABankAccount;
    @Schema(description = "甲方电话", example = "028-88888888")
    private String partyATel;
    @Schema(description = "甲方传真", example = "028-88888889")
    private String partyAFax;

    // ---------- 乙方（供应商）主体信息 ----------
    @Schema(description = "乙方单位名称", example = "天信防腐保温有限公司")
    private String partyBName;
    @Schema(description = "乙方法定代表人", example = "王五")
    private String partyBLegalPerson;
    @Schema(description = "乙方授权代表/授权委托人", example = "赵六")
    private String partyBAgent;
    @Schema(description = "乙方地址", example = "成都市xx路2号")
    private String partyBAddress;
    @Schema(description = "乙方开户银行", example = "中国建设银行xx支行")
    private String partyBBankName;
    @Schema(description = "乙方银行账号", example = "0987654321")
    private String partyBBankAccount;
    @Schema(description = "乙方电话", example = "028-66666666")
    private String partyBTel;
    @Schema(description = "乙方传真", example = "028-66666667")
    private String partyBFax;

    @Schema(description = "违约责任条款（JSON）")
    private String penaltyTerms;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "创建人", example = "25682")
    @ExcelProperty("创建人")
    private String creator;

    @Schema(description = "创建人名字", example = "test")
    @ExcelProperty("创建人名字")
    private String creatorName;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "产品列表")
    private List<Product> products;

    @Schema(description = "产品列表")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {

        @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "888")
        private Long id;

        @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20529")
        private Long productId;
        @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
        private String productName;
        @Schema(description = "产品条码", requiredMode = Schema.RequiredMode.REQUIRED, example = "20529")
        private String productNo;
        @Schema(description = "产品单位", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
        private Integer productUnit;

        @Schema(description = "产品单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal productPrice;

        @Schema(description = "合同价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal contractPrice;

        @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
        private BigDecimal count;

        @Schema(description = "总计价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal totalPrice;

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
