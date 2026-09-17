package cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    @Schema(description = "仓库类型：RAW_MATERIAL 原材料仓、FINISHED_GOODS 成品仓", example = "RAW_MATERIAL")
    @Pattern(regexp = "RAW_MATERIAL|FINISHED_GOODS", message = "仓库类型必须为原材料仓或成品仓")
    private String warehouseType;

    @Schema(description = "仓库状态", example = "0")
    private Integer status;

    @Schema(description = "所属组织/部门", example = "供应链部")
    @Size(max = 100, message = "所属组织/部门长度不能超过 100 个字符")
    private String organizationName;

    @Schema(description = "所在城市", example = "上海市")
    @Size(max = 50, message = "所在城市长度不能超过 50 个字符")
    private String city;

    @Schema(description = "仓库地址", example = "浦东新区张江路 100 号")
    @Size(max = 255, message = "仓库地址长度不能超过 255 个字符")
    private String address;

    @Schema(description = "负责人", example = "张三")
    @Size(max = 64, message = "负责人长度不能超过 64 个字符")
    private String principal;

    @Schema(description = "负责人联系电话", example = "13800138000")
    @Pattern(regexp = "^$|^1\\d{10}$", message = "负责人联系电话须为 11 位手机号码")
    private String principalPhone;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "排序不能为空")
    private Integer sort;

    @Schema(description = "备注", example = "备注")
    private String remark;

}
