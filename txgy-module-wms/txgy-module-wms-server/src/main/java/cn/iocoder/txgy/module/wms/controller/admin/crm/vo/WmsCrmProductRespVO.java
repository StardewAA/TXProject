package cn.iocoder.txgy.module.wms.controller.admin.crm.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
}
