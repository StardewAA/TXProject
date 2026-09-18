package cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - WMS 仓库新增/修改 Request VO")
@Data
public class WmsWarehouseSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "WH001")
    @NotEmpty(message = "仓库编号不能为空")
    @Size(max = 20, message = "仓库编号长度不能超过 20 个字符")
    private String code;

    @Schema(description = "仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "原料仓")
    @NotEmpty(message = "仓库名称不能为空")
    private String name;

    @Schema(description = "仓库类型：RAW_MATERIAL 原材料仓、FINISHED_GOODS 成品仓")
    @jakarta.validation.constraints.Pattern(regexp = "RAW_MATERIAL|FINISHED_GOODS", message = "仓库类型必须为原材料仓或成品仓")
    private String warehouseType;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "排序不能为空")
    private Integer sort;

    @Schema(description = "备注", example = "备注")
    private String remark;

    @Schema(description = "仓库负责人用户编号")
    private Long managerUserId;

    @Schema(description = "仓库员工用户编号集合")
    private Set<Long> memberUserIds;

}
