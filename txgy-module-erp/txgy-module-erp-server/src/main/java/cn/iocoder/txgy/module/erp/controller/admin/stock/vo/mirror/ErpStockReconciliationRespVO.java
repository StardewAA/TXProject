package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 库存对账（业务账 vs 实物账）Response VO
 *
 * <p>业务账 erp_stock 由 ERP 单据推演；实物账 erp_stock_mirror 同步自 WMS。
 * 两者差额即「待查原因」，典型三类：</p>
 * <ol>
 *     <li>业务账 &gt; 实物账：ERP 单据没成功推到 WMS，或 WMS 侧有 ERP 不知道的出库</li>
 *     <li>业务账 &lt; 实物账：商城发货、MES 领料、WMS 手工调整未同步回 ERP</li>
 *     <li>数量不等：部分收货 / 部分发货 / 单位换算错误</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - ERP 库存对账 Response VO")
@Data
public class ErpStockReconciliationRespVO {

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long productId;

    @Schema(description = "产品编码", example = "CP-1001")
    private String productCode;

    @Schema(description = "产品名称", example = "环氧富锌底漆")
    private String productName;

    @Schema(description = "仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Long warehouseId;

    @Schema(description = "仓库名称", example = "天信管业仓库1号")
    private String warehouseName;

    @Schema(description = "业务账数量（ERP 单据推演）", example = "277")
    private BigDecimal businessCount;

    @Schema(description = "实物账数量（WMS 同步）", example = "277")
    private BigDecimal mirrorCount;

    @Schema(description = "差额 = 业务账 - 实物账", example = "0")
    private BigDecimal diff;

    @Schema(description = "实物账最近同步时间")
    private LocalDateTime mirrorSyncTime;

}
