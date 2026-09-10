package cn.iocoder.txgy.module.wms.api.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RPC 服务 - ERP/WMS 统一业务单据明细")
@Data
public class WmsIntegrationDocumentItemDTO {

    @NotNull(message = "lineNo 不能为空")
    private Integer lineNo;

    private Integer sourceLineNo;

    @NotBlank(message = "materialCode 不能为空")
    private String materialCode;

    @NotBlank(message = "skuCode 不能为空")
    private String skuCode;

    @NotBlank(message = "materialType 不能为空")
    private String materialType;

    @NotBlank(message = "物料名称快照不能为空")
    private String materialNameSnapshot;

    private String specificationSnapshot;

    @NotBlank(message = "unitCode 不能为空")
    private String unitCode;

    @NotBlank(message = "单位名称快照不能为空")
    private String unitNameSnapshot;

    private String warehouseCode;
    private String batchNo;

    @DecimalMin(value = "0.000001", message = "需求数量必须大于 0")
    private BigDecimal requiredQuantity;

    @DecimalMin(value = "0", message = "预留数量不能小于 0")
    private BigDecimal reservedQuantity;

    @DecimalMin(value = "0", message = "释放数量不能小于 0")
    private BigDecimal releasedQuantity;

    @DecimalMin(value = "0", message = "实际数量不能小于 0")
    private BigDecimal actualQuantity;

    @DecimalMin(value = "0", message = "合格数量不能小于 0")
    private BigDecimal qualifiedQuantity;

    @DecimalMin(value = "0", message = "不合格数量不能小于 0")
    private BigDecimal unqualifiedQuantity;

    @DecimalMin(value = "0", message = "缺口数量不能小于 0")
    private BigDecimal shortageQuantity;

    private Boolean qualityRequiredSnapshot;

    @Valid
    private List<WmsIntegrationAttachmentDTO> attachments;

    private String sourceReceiptOrderNo;
    private Integer sourceReceiptLineNo;

}
