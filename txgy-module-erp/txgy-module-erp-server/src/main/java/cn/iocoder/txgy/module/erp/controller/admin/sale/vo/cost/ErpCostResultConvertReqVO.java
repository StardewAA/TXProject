package cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ErpCostResultConvertReqVO {

    /** 转换类型：销售报价 */
    public static final Integer TYPE_SALE_QUOTATION = 1;
    /** 转换类型：销售订单（会真实创建销售订单并回填 convertedId） */
    public static final Integer TYPE_SALE_ORDER = 2;
    /** 转换类型：项目预算 */
    public static final Integer TYPE_PROJECT_BUDGET = 3;
    /** 转换类型：归档 */
    public static final Integer TYPE_ARCHIVE = 4;

    @Schema(description = "造价结果编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "造价结果不能为空")
    private Long id;

    @Schema(description = "转换类型：1 销售报价 / 2 销售订单 / 3 项目预算 / 4 归档",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "转换类型不能为空")
    private Integer convertedType;

    // ==================== 以下仅在 convertedType = 2（销售订单）时需要 ====================

    @Schema(description = "客户编号（转销售订单时必填）", example = "7")
    private Long customerId;

    @Schema(description = "销售员编号（转销售订单时可选）", example = "80000")
    private Long saleUserId;

    @Schema(description = "结算账户编号（转销售订单时可选）", example = "3")
    private Long accountId;

    @Schema(description = "WMS 统一仓库编码（转销售订单时可选）", example = "WH01")
    private String warehouseCode;

}
