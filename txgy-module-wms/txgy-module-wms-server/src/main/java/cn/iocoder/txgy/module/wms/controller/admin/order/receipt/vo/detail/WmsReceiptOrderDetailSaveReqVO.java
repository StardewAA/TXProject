package cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.detail;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - WMS 入库单明细保存 Request VO")
@Data
public class WmsReceiptOrderDetailSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Schema(description = "来源明细行号，由服务端写入", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer sourceLineNo;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Schema(description = "创建时来源明细快照，仅由集成服务写入", accessMode = Schema.AccessMode.READ_ONLY)
    private String sourceSnapshotJson;

    @Schema(description = "SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @Schema(description = "入库数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "100.00")
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "入库数量必须大于 0")
    private BigDecimal quantity;
    private BigDecimal actualQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;

    private java.util.List<String> qualityImages;
    private String qualityRemark;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Boolean qualityRequiredSnapshot;

    @Schema(description = "单价", example = "1000.00")
    @DecimalMin(value = "0", message = "单价不能小于 0")
    private BigDecimal price;

    @Schema(description = "行金额", example = "1500.00")
    @DecimalMin(value = "0", message = "行金额不能小于 0")
    private BigDecimal totalPrice;

}
