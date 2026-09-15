package cn.iocoder.txgy.module.erp.api.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单项 Response DTO
 *
 * 供 MES 等下游模块消费，用于按订单行生成生产工单。
 * 产品主数据在 ERP 与 MES 为两套独立主数据，通过 ERP 的 material_code 与 MES 物料 code 关联。
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 销售订单项 Response DTO")
@Data
public class ErpSaleOrderItemRespDTO {

    @Schema(description = "订单项编号")
    private Long id;

    @Schema(description = "产品编号（ERP 产品）", example = "1")
    private Long productId;

    @Schema(description = "产品名称", example = "防腐保温管-08")
    private String productName;

    @Schema(description = "产品条码", example = "M-FG-001")
    private String productBarCode;

    @Schema(description = "产品单位编号", example = "1")
    private Long productUnitId;

    @Schema(description = "产品数量", example = "100.00")
    private BigDecimal count;

    @Schema(description = "ERP 物料编码（material_code），用于与 MES 物料关联", example = "ERP-MAT-000000000001")
    private String materialCode;

}
