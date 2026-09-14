package cn.iocoder.txgy.module.erp.api.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "RPC 模块 - 销售订单行明细 Response DTO")
@Data
public class ErpSaleOrderItemRespDTO {

    @Schema(description = "ERP 产品编号", example = "1")
    private Long productId;

    @Schema(description = "产品名称（ERP 侧提前组装，避免下游反向依赖）", example = "预制保温管 DN100")
    private String productName;

    @Schema(description = "产品单位名称", example = "米")
    private String productUnitName;

    @Schema(description = "订购数量", example = "100.00")
    private BigDecimal count;

    @Schema(description = "金额合计", example = "50000.00")
    private BigDecimal totalPrice;

}
