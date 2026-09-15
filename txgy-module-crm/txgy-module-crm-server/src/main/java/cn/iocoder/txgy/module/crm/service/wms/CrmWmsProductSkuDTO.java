package cn.iocoder.txgy.module.crm.service.wms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * WMS 商品规格（SKU）只读数据，CRM 产品管理的实时数据源。
 *
 * <p>对应 {@code GET /admin-api/wms/crm/products/page}、{@code /get}、{@code /list} 返回的每一行，一个 SKU 一行。
 * 这里的 itemId / skuId 是 WMS 侧标识，CRM 直接以 skuId 作为产品编号使用。</p>
 */
@Schema(description = "CRM 侧 WMS 商品规格只读数据")
@Data
public class CrmWmsProductSkuDTO {

    @Schema(description = "WMS 商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long itemId;

    @Schema(description = "WMS 规格编号，即 CRM 产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    private Long skuId;

    @Schema(description = "WMS 商品编码", example = "DEMO-PIPE")
    private String materialCode;

    @Schema(description = "WMS 商品名称", example = "示例管材")
    private String materialName;

    @Schema(description = "WMS 规格编码", example = "DEMO-PIPE-20")
    private String skuCode;

    @Schema(description = "WMS 规格名称", example = "20mm")
    private String skuName;

    @Schema(description = "WMS 商品单位，未维护时可为空", example = "米")
    private String unitName;

    @Schema(description = "WMS 销售单价，单位：元；未维护时可为空", example = "88.00")
    private BigDecimal sellingPrice;

    @Schema(description = "WMS 仓库库存数量，按所有仓库汇总；无库存记录时为 0", example = "120")
    private BigDecimal quantity;

}
