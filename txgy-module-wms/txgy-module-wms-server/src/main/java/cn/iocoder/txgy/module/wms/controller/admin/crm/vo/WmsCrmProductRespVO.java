package cn.iocoder.txgy.module.wms.controller.admin.crm.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "CRM 商品只读响应，每个 SKU 一行")
public class WmsCrmProductRespVO {
    @Schema(description = "WMS 商品 ID，不是 CRM 商品 ID")
    private Long itemId;
    @Schema(description = "WMS SKU ID，不是 CRM 商品 ID")
    private Long skuId;
    private String materialCode;
    private String materialName;
    private String skuCode;
    @Schema(description = "规格名称")
    private String skuName;
    private String unitName;
    @Schema(description = "销售单价，单位：元；未维护时为空")
    private BigDecimal sellingPrice;
    @Schema(description = "仓库库存数量，按当前租户下所有仓库汇总；无库存记录时为 0")
    private BigDecimal quantity;
}
