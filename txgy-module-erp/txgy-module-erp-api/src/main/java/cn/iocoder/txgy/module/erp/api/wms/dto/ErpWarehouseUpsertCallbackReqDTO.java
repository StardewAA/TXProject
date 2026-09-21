package cn.iocoder.txgy.module.erp.api.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * WMS 推送给 ERP 的仓库主数据 upsert 请求 DTO。
 *
 * <p>WMS 端在 createWarehouse / updateWarehouse / deleteWarehouse 时，
 * 通过 RPC 调用 ERP 的 {@code /rpc-api/erp/wms-callback/warehouse-upsert} 端点，
 * ERP 端按 {@code warehouseCode} 匹配 {@link cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO#getWmsWarehouseCode()}
 * 进行 upsert。</p>
 */
@Schema(description = "RPC 服务 - WMS 仓库主数据 upsert 请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpWarehouseUpsertCallbackReqDTO extends ErpWmsCallbackBaseReqDTO {

    @Schema(description = "WMS 权威仓库编码（与 ERP 端 erp_warehouse.wms_warehouse_code 对应）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "warehouseCode 不能为空")
    private String warehouseCode;

    @Schema(description = "仓库名称")
    private String name;

    @Schema(description = "仓库类型：RAW_MATERIAL 原料仓 / FINISHED_GOODS 成品仓")
    private String warehouseType;

    @Schema(description = "仓库地址")
    private String address;

    @Schema(description = "负责人")
    private String principal;

    @Schema(description = "状态：ENABLED 启用 / DISABLED 禁用")
    @NotBlank(message = "status 不能为空")
    private String status;

    @Schema(description = "来源模块：WMS")
    private String sourceModule;

}
