package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售绩效归因 Response VO
 *
 * <p>回答「哪些是 ERP 的什么人销售的」：以库存流水的责任人快照归因，
 * 按「销售员 × 产品」聚合销售出库与销售退货。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 销售绩效归因 Response VO")
@Data
public class ErpSalesAttributionRespVO {

    @Schema(description = "销售员用户编号", example = "80000")
    private Long userId;

    @Schema(description = "销售员姓名", example = "王小军")
    private String userName;

    @Schema(description = "产品编码", example = "CP-1001")
    private String productCode;

    @Schema(description = "产品名称", example = "环氧富锌底漆")
    private String productName;

    @Schema(description = "销售出库数量", example = "200")
    private BigDecimal outCount;

    @Schema(description = "销售退货数量", example = "15")
    private BigDecimal returnCount;

    @Schema(description = "净销售数量 = 出库 - 退货", example = "185")
    private BigDecimal netCount;

    @Schema(description = "销售出库金额", example = "112000")
    private BigDecimal outAmount;

    @Schema(description = "销售退货金额", example = "8400")
    private BigDecimal returnAmount;

    @Schema(description = "净销售金额 = 出库金额 - 退货金额", example = "103600")
    private BigDecimal netAmount;

}
