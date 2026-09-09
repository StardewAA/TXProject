package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpWmsDocumentItemDTO {

    @NotNull(message = "lineNo 不能为空")
    private Integer lineNo;

    private Integer sourceLineNo;

    @NotBlank(message = "materialCode 不能为空")
    private String materialCode;

    @NotBlank(message = "skuCode 不能为空")
    private String skuCode;

    @NotBlank(message = "物料类型不能为空")
    private String materialType;
    @NotBlank(message = "物料名称快照不能为空")
    private String materialNameSnapshot;
    private String specificationSnapshot;
    @NotBlank(message = "单位编码不能为空")
    private String unitCode;
    @NotBlank(message = "单位名称快照不能为空")
    private String unitNameSnapshot;
    private Boolean qualityRequiredSnapshot;

    private String warehouseCode;
    private String batchNo;
    private BigDecimal requiredQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal releasedQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;
    private BigDecimal shortageQuantity;

    /** 参考价格（供应链协同单扩展字段，可空） */
    private BigDecimal referencePrice;
    /** 参考税率（供应链协同单扩展字段，可空） */
    private BigDecimal taxRate;
    /** 收货/交货地址（供应链协同单扩展字段，可空） */
    private String deliveryAddress;

}
