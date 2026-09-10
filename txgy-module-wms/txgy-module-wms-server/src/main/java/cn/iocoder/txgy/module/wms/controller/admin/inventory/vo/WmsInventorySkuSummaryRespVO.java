package cn.iocoder.txgy.module.wms.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "WMS 只读商品规格价格及库存汇总")
public class WmsInventorySkuSummaryRespVO {
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String unit;
    @Schema(description = "WMS 销售价，单位元；null 表示未配置，不是免费")
    private BigDecimal sellingPrice;
    @Schema(description = "当前租户所有仓库的现存数量合计，非可售或可承诺库存")
    private BigDecimal totalQuantity;
}
