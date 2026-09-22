package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存出库来源构成 Response VO
 *
 * <p>回答「仓库里多少是 ERP 销售的」：按来源系统 + 业务类型分组。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 库存出库来源构成 Response VO")
@Data
public class ErpStockSourceBreakdownRespVO {

    @Schema(description = "来源系统", example = "ERP")
    private String sourceSystem;

    @Schema(description = "来源系统名称", example = "ERP 系统")
    private String sourceSystemName;

    @Schema(description = "业务类型", example = "50")
    private Integer bizType;

    @Schema(description = "业务类型名称", example = "销售出库")
    private String bizTypeName;

    @Schema(description = "变动量合计（带符号：正=入库，负=出库）", example = "-55023")
    private BigDecimal totalCount;

    @Schema(description = "流水条数", example = "10")
    private Integer recordCount;

    @Schema(description = "是否属于 ERP 销售（sourceSystem=ERP 且 bizType=50）", example = "true")
    private Boolean erpSale;

}
