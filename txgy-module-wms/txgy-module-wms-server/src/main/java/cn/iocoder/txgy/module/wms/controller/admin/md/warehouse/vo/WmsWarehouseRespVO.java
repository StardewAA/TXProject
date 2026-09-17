package cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WMS 仓库 Response VO")
@Data
@ExcelIgnoreUnannotated
public class WmsWarehouseRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "仓库编号", example = "WH001")
    @ExcelProperty("仓库编号")
    private String code;

    @Schema(description = "仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "原料仓")
    @ExcelProperty("仓库名称")
    private String name;

    @Schema(description = "仓库类型", example = "RAW_MATERIAL")
    @ExcelProperty("仓库类型")
    private String warehouseType;

    @Schema(description = "仓库状态", example = "0")
    @ExcelProperty("仓库状态")
    private Integer status;

    @Schema(description = "所属组织/部门", example = "供应链部")
    @ExcelProperty("所属组织/部门")
    private String organizationName;

    @Schema(description = "所在城市", example = "上海市")
    @ExcelProperty("所在城市")
    private String city;

    @Schema(description = "仓库地址", example = "浦东新区张江路 100 号")
    @ExcelProperty("仓库地址")
    private String address;

    @Schema(description = "负责人", example = "张三")
    @ExcelProperty("负责人")
    private String principal;

    @Schema(description = "负责人联系电话", example = "13800138000")
    private String principalPhone;

    @Schema(description = "备注", example = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "创建人")
    @ExcelProperty("创建人")
    private String creator;

    @Schema(description = "创建人名称")
    private String creatorName;

    @Schema(description = "最后更新时间")
    @ExcelProperty("最后更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "最后更新人")
    @ExcelProperty("最后更新人")
    private String updater;

    @Schema(description = "最后更新人名称")
    private String updaterName;

}
