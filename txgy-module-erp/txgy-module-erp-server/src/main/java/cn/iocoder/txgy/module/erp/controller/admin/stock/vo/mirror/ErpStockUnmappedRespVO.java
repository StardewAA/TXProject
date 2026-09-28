package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * ERP 库存映射缺失诊断 Response VO
 *
 * <p>实物账同步依赖两条映射链，任一断裂都会导致镜像为空：</p>
 * <ul>
 *     <li>仓库：erp_warehouse.wms_warehouse_code → wms_warehouse.code</li>
 *     <li>产品：erp_product.material_code → wms_item.code → wms_item_sku.code = material_code + '-DEFAULT'</li>
 * </ul>
 *
 * <p>本 VO 用于把「为什么同步不出来」直接暴露出来，而不是静默返回 0 条。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - ERP 库存映射缺失诊断 Response VO")
@Data
public class ErpStockUnmappedRespVO {

    @Schema(description = "已配置 WMS 编码、但 WMS 侧不存在该仓库的 ERP 仓库编码列表", example = "[\"WH01\",\"WH02\"]")
    private List<String> unmappedWarehouseCodes;

    @Schema(description = "尚未推送到 WMS（WMS 侧无同编码物料）的 ERP 产品编码列表")
    private List<String> unmappedProductCodes;

    @Schema(description = "是否具备同步条件（无仓库缺失且无产品缺失）", example = "false")
    private Boolean syncReady;

}
