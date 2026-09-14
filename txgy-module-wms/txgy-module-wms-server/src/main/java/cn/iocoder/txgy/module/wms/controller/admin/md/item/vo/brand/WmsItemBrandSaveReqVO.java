package cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - WMS 商品品牌新增/修改 Request VO")
@Data
public class WmsItemBrandSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "品牌编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "B00000001")
    @NotEmpty(message = "品牌编号不能为空")
    @Size(max = 20, message = "品牌编号长度不能超过 20 个字符")
    private String code;

    @Schema(description = "品牌名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "华为")
    @NotEmpty(message = "品牌名称不能为空")
    @Size(max = 30, message = "品牌名称长度不能超过 30 个字符")
    private String name;

    @Schema(description = "关联商品分类编号", example = "3200201")
    private Long categoryId;

    @Schema(description = "主要供应商编号", example = "3200101")
    private Long supplierId;

    @Schema(description = "品牌说明", example = "国际知名防腐涂料品牌")
    @Size(max = 500, message = "品牌说明长度不能超过 500 个字符")
    private String description;

    @Schema(description = "资质文件地址，多个以逗号分隔")
    @Size(max = 2000, message = "资质文件地址长度不能超过 2000 个字符")
    private String qualificationFiles;

}
