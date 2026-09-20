package cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 共享仓库商品 Response VO")
@Data
public class WmsSharedWarehouseProductRespVO {

    @Schema(description = "WMS 仓库编号", example = "2")
    private Long warehouseId;

    @Schema(description = "仓库名称", example = "北京仓")
    private String warehouseName;

    @Schema(description = "WMS SKU 编号", example = "20017")
    private Long wmsSkuId;

    @Schema(description = "WMS SKU 名称", example = "5kg 箱装")
    private String wmsSkuName;

    @Schema(description = "WMS 商品编号", example = "10012")
    private Long wmsItemId;

    @Schema(description = "WMS 商品名称", example = "红富士苹果")
    private String wmsItemName;

    @Schema(description = "条码", example = "690100120001")
    private String barCode;

    @Schema(description = "实物库存", example = "73.00")
    private BigDecimal physicalQuantity;

    @Schema(description = "来源系统", example = "[\"WMS\",\"MALL\"]")
    private List<String> sourceSystems = new ArrayList<>();

    @Schema(description = "商城 SKU 编号")
    private Long mallSkuId;

    @Schema(description = "商城 SPU 编号")
    private Long mallSpuId;

    @Schema(description = "商城商品名称")
    private String mallSpuName;

    @Schema(description = "商城缓存库存")
    private Integer mallCachedStock;

    @Schema(description = "ERP 产品编号")
    private Long erpProductId;

    @Schema(description = "ERP 产品名称")
    private String erpProductName;

}
