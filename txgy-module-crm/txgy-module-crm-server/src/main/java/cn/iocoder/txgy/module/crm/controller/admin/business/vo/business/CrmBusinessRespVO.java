package cn.iocoder.txgy.module.crm.controller.admin.business.vo.business;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CRM 商机 Response VO")
@Data
@ExcelIgnoreUnannotated
public class CrmBusinessRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "32129")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "商机名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @ExcelProperty("商机名称")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10299")
    private Long customerId;
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @ExcelProperty("客户名称")
    private String customerName;

    @Schema(description = "跟进状态", requiredMode = Schema.RequiredMode.REQUIRED, example ="true")
    @ExcelProperty("跟进状态")
    private Boolean followUpStatus;

    @Schema(description = "最后跟进时间")
    @ExcelProperty("最后跟进时间")
    private LocalDateTime contactLastTime;

    @Schema(description = "下次联系时间")
    @ExcelProperty("下次联系时间")
    private LocalDateTime contactNextTime;

    @Schema(description = "负责人的用户编号", example = "25682")
    @ExcelProperty("负责人的用户编号")
    private Long ownerUserId;
    @Schema(description = "负责人名字", example = "25682")
    @ExcelProperty("负责人名字")
    private String ownerUserName;
    @Schema(description = "负责人部门")
    @ExcelProperty("负责人部门")
    private String ownerUserDeptName;

    @Schema(description = "商机状态组编号（历史字段）", example = "25714")
    private Long statusTypeId;
    @Schema(description = "商机状态组名字（历史字段）", example = "进行中")
    @ExcelProperty("商机状态组(历史)")
    private String statusTypeName;

    @Schema(description = "商机状态编号（历史字段）", example = "30320")
    private Long statusId;
    @Schema(description = "状态名称（历史字段）", example = "跟进中")
    @ExcelProperty("商机状态(历史)")
    private String statusName;

    @Schema(description = "商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判", example = "3")
    @ExcelProperty("商机阶段")
    private Integer stage;
    @Schema(description = "商机阶段名称", example = "需求确认")
    private String stageName;

    @Schema(description = "商机状态组：1未开始 2进行中 3已赢单 4已丢单（只读派生）", example = "2")
    @ExcelProperty("商机状态组")
    private Integer businessState;
    @Schema(description = "商机状态组名称", example = "进行中")
    private String businessStateName;

    @Schema(description = "预估赢单率(%)", example = "40")
    @ExcelProperty("预估赢单率(%)")
    private Integer winRate;

    @Schema(description = "丢单原因", example = "2")
    private Integer lostReason;
    @Schema(description = "丢单原因名称", example = "客户选择竞品")
    private String lostReasonName;
    @Schema(description = "丢单备注", example = "客户已选定其他供应商")
    private String lostRemark;

    @Schema(description = "签约日期")
    private LocalDateTime signDate;
    @Schema(description = "签约金额", example = "120000.00")
    private BigDecimal signAmount;

    @Schema(description = "关闭时间")
    private LocalDateTime closedAt;
    @Schema(description = "关闭操作人用户编号")
    private Long closedBy;
    @Schema(description = "关闭操作人名称")
    private String closedByName;

    @Schema
    @ExcelProperty("结束状态")
    private Integer endStatus;

    @ExcelProperty("结束时的备注")
    private String endRemark;

    @Schema(description = "预计成交日期")
    @ExcelProperty("预计成交日期")
    private LocalDateTime dealTime;

    @Schema(description = "产品总金额", example = "12025")
    @ExcelProperty("产品总金额")
    private BigDecimal totalProductPrice;

    @Schema(description = "整单折扣")
    @ExcelProperty("整单折扣")
    private BigDecimal discountPercent;

    @Schema(description = "商机总金额", example = "12371")
    @ExcelProperty("商机总金额")
    private BigDecimal totalPrice;

    @Schema(description = "备注", example = "随便")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建人", example = "1024")
    @ExcelProperty("创建人")
    private String creator;
    @Schema(description = "创建人名字", example = "OPENLAB BS")
    @ExcelProperty("创建人名字")
    private String creatorName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

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

        @Schema(description = "商机价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal businessPrice;

        @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8911")
        private BigDecimal count;

        @Schema(description = "总计价格", requiredMode = Schema.RequiredMode.REQUIRED, example = "123.00")
        private BigDecimal totalPrice;

    }

}
