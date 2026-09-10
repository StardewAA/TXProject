package cn.iocoder.txgy.module.erp.controller.admin.product.vo.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - ERP 产品新增/修改 Request VO")
@Data
public class ProductSaveReqVO {

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15672")
    private Long id;

    @Schema(description = "跨模块统一物料编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "MAT-FG-0001")
    @NotEmpty(message = "统一物料编码不能为空")
    private String materialCode;

    @Schema(description = "物料类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "FINISHED_GOODS")
    @NotEmpty(message = "物料类型不能为空")
    @Pattern(regexp = "RAW_MATERIAL|FINISHED_GOODS", message = "物料类型必须是 RAW_MATERIAL 或 FINISHED_GOODS")
    private String materialType;

    @Schema(description = "是否需要质检", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "质检标志不能为空")
    private Boolean qualityRequired;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @NotEmpty(message = "产品名称不能为空")
    private String name;

    @Schema(description = "产品条码", requiredMode = Schema.RequiredMode.REQUIRED, example = "X110")
    @NotEmpty(message = "产品条码不能为空")
    private String barCode;

    @Schema(description = "产品分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11161")
    @NotNull(message = "产品分类编号不能为空")
    private Long categoryId;

    @Schema(description = "单位编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "8869")
    @NotNull(message = "单位编号不能为空")
    private Long unitId;

    @Schema(description = "产品状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "产品状态不能为空")
    private Integer status;

    @Schema(description = "产品规格", example = "红色")
    private String standard;

    @Schema(description = "产品备注", example = "你猜")
    private String remark;

    @Schema(description = "保质期天数", example = "10")
    private Integer expiryDay;

    @Schema(description = "基础重量（kg）", example = "1.00")
    private BigDecimal weight;

    @Schema(description = "采购价格，单位：元", example = "10.30")
    private BigDecimal purchasePrice;

    @Schema(description = "销售价格，单位：元", example = "74.32")
    private BigDecimal salePrice;

    @Schema(description = "最低价格，单位：元", example = "161.87")
    private BigDecimal minPrice;

}
