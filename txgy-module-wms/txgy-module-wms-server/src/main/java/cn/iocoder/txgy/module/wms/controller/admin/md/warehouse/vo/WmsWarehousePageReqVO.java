package cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - WMS 仓库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WmsWarehousePageReqVO extends PageParam {

    @Schema(description = "仓库编号", example = "WH001")
    private String code;

    @Schema(description = "仓库名称", example = "原料仓")
    private String name;

    @Schema(description = "仓库类型", example = "RAW_MATERIAL")
    private String warehouseType;

    @Schema(description = "仓库状态", example = "0")
    private Integer status;

    @Schema(description = "所属组织/部门")
    private String organizationName;

    @Schema(description = "所在城市")
    private String city;

    @Schema(description = "仓库地址")
    private String address;

    @Schema(description = "负责人")
    private String principal;

}
