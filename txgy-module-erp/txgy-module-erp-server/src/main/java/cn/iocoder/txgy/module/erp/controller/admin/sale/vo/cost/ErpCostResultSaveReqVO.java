package cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ErpCostResultSaveReqVO {
    private Long id;
    @NotBlank(message = "造价项目名称不能为空")
    private String name;
    private String projectCode;
    @NotNull(message = "造价版本不能为空")
    private Integer costVersion;
    private Integer sourceType;
    private String currency;
    private String fileUrl;
    private String remark;
    @Valid
    @NotEmpty(message = "造价明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        @NotNull(message = "行号不能为空")
        private Integer lineNo;
        private String externalMaterialCode;
        @NotBlank(message = "材料名称不能为空")
        private String materialName;
        private String specification;
        private String unitName;
        private Long productId;
        private Long productUnitId;
        private Integer mappingStatus;
        @NotNull(message = "数量不能为空")
        private BigDecimal count;
        @NotNull(message = "单价不能为空")
        private BigDecimal unitPrice;
        private BigDecimal taxPercent;
        private String remark;
    }
}
