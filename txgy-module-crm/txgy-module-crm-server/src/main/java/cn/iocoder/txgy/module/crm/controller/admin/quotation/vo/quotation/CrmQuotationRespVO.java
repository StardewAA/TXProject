package cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CRM 报价单 Response VO")
@Data
@ExcelIgnoreUnannotated
public class CrmQuotationRespVO {

    @Schema(description = "报价编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10430")
    @ExcelProperty("报价编号")
    private Long id;

    @Schema(description = "报价名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐保温工程报价")
    @ExcelProperty("报价名称")
    private String name;

    @Schema(description = "报价单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "BJ20260825000001")
    @ExcelProperty("报价单号")
    private String no;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "18336")
    @ExcelProperty("客户编号")
    private Long customerId;
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @ExcelProperty("客户名称")
    private String customerName;

    @Schema(description = "商机编号", example = "10864")
    @ExcelProperty("商机编号")
    private Long businessId;
    @Schema(description = "商机名称", example = "商机ABC")
    @ExcelProperty("商机名称")
    private String businessName;

    @Schema(description = "负责人的用户编号", example = "25682")
    @ExcelProperty("负责人编号")
    private Long ownerUserId;
    @Schema(description = "负责人名字", example = "小王")
    @ExcelProperty("负责人")
    private String ownerUserName;
    @Schema(description = "负责人部门", example = "销售一部")
    @ExcelProperty("负责人部门")
    private String ownerUserDeptName;

    @Schema(description = "工作流编号", example = "1043")
    @ExcelProperty("工作流编号")
    private String processInstanceId;

    @Schema(description = "审批状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @ExcelProperty("审批状态")
    private Integer auditStatus;

    @Schema(description = "报价日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("报价日期")
    private LocalDateTime orderDate;

    @Schema(description = "有效期至")
    @ExcelProperty("有效期至")
    private LocalDateTime validUntil;

    @Schema(description = "产品总金额", example = "19510")
    @ExcelProperty("产品总金额")
    private BigDecimal totalProductPrice;

    @Schema(description = "整单折扣")
    @ExcelProperty("整单折扣")
    private BigDecimal discountPercent;

    @Schema(description = "报价金额", example = "5617")
    @ExcelProperty("报价金额")
    private BigDecimal totalPrice;

    @Schema(description = "生成的合同编号", example = "10430")
    @ExcelProperty("合同编号")
    private Long contractId;

    @Schema(description = "备注", example = "你猜")
    @ExcelProperty("备注")
    private String remark;

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
        @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐涂料")
        private String productName;
        @Schema(description = "产品条码", requiredMode = Schema.RequiredMode.REQUIRED, example = "20529")
        private String productNo;
        @Schema(description = "产品单位", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer productUnit;

        @Schema(description = "产品单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal productPrice;

        @Schema(description = "报价价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal quotationPrice;

        @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
        private BigDecimal count;

        @Schema(description = "总计价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal totalPrice;

    }

}
