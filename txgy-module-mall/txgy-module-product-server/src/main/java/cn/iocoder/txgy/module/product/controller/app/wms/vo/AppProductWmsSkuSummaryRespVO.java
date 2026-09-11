package cn.iocoder.txgy.module.product.controller.app.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "用户 App - WMS 商品规格、售价及库存摘要 Response VO")
@Data
public class AppProductWmsSkuSummaryRespVO {

    @Schema(description = "WMS 商品编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @Schema(description = "WMS 商品编码")
    private String itemCode;

    @Schema(description = "WMS 商品名称")
    private String itemName;

    @Schema(description = "WMS 规格编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @Schema(description = "WMS 规格编码")
    private String skuCode;

    @Schema(description = "WMS 规格名称")
    private String skuName;

    @Schema(description = "计量单位")
    private String unit;

    @Schema(description = "销售价，单位：元")
    private BigDecimal sellingPrice;

    @Schema(description = "当前租户该规格总库存")
    private BigDecimal totalQuantity;

}
