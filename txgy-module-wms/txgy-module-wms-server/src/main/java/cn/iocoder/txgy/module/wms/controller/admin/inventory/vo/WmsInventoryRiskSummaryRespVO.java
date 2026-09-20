package cn.iocoder.txgy.module.wms.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - WMS 库存预警与积压风险汇总 Response VO")
@Data
public class WmsInventoryRiskSummaryRespVO {

    @Schema(description = "库存预警列表")
    private List<RiskItem> inventoryWarnings;

    @Schema(description = "积压风险列表")
    private List<RiskItem> inventoryRisks;

    @Data
    public static class RiskItem {
        private Long itemId;
        private String itemCode;
        private String itemName;
        private Long skuId;
        private String skuCode;
        private String skuName;
        private Long warehouseId;
        private String warehouseName;
        private BigDecimal quantity;
        private String level;
        private String label;
        private String reason;
        private Integer daysSinceOutbound;
    }
}
