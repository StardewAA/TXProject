package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsWarehouseUpsertReqDTO extends WmsIntegrationBaseReqDTO {

    private Long warehouseId;
    private String warehouseCode;

    @NotBlank(message = "仓库名称不能为空")
    private String name;

    @NotBlank(message = "warehouseType 不能为空")
    private String warehouseType;

    private String address;
    private String principal;

    @NotBlank(message = "status 不能为空")
    private String status;

    private Integer sort;
    private String remark;

    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

    private Long expectedVersion;

}
