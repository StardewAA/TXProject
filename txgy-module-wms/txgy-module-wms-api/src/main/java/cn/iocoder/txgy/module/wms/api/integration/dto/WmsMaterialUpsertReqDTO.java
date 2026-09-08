package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsMaterialUpsertReqDTO extends WmsIntegrationBaseReqDTO {

    @NotBlank(message = "materialCode 不能为空")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    private String name;

    @NotBlank(message = "materialType 不能为空")
    private String materialType;

    private String specification;

    @NotBlank(message = "unitCode 不能为空")
    private String unitCode;

    @NotBlank(message = "单位名称不能为空")
    private String unitName;

    private String barCode;

    @NotNull(message = "qualityRequired 不能为空")
    private Boolean qualityRequired;

    private BigDecimal weight;

    @NotBlank(message = "status 不能为空")
    private String status;

    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

}
