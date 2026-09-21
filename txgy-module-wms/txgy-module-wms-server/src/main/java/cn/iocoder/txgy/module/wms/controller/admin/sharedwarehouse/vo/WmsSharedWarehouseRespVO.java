package cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 共享仓库 Response VO")
@Data
public class WmsSharedWarehouseRespVO {

    @Schema(description = "WMS 仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long warehouseId;

    @Schema(description = "仓库编码", example = "BJ")
    private String warehouseCode;

    @Schema(description = "仓库名称", example = "北京仓")
    private String warehouseName;

    @Schema(description = "实物仓归属系统，固定为 WMS", example = "WMS")
    private String ownerSystem;

    @Schema(description = "仓内可见来源系统", example = "[\"WMS\",\"MALL\"]")
    private List<String> sourceSystems = new ArrayList<>();

    @Schema(description = "WMS SKU 数量", example = "8")
    private Long wmsSkuCount;

    @Schema(description = "已映射商城 SKU 数量", example = "2")
    private Long mallSkuCount;

    @Schema(description = "按条码匹配到的 ERP 产品数量", example = "1")
    private Long erpProductCount;

    @Schema(description = "实物库存合计", example = "320.00")
    private BigDecimal physicalQuantity;

    @Schema(description = "名称相同的 ERP 经营仓，仅作对照，不代表实物库存")
    private List<String> relatedErpWarehouseNames = new ArrayList<>();

    @Schema(description = "备注")
    private String remark;

}
