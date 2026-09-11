package cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WMS 商品品牌 Response VO")
@Data
@ExcelIgnoreUnannotated
public class WmsItemBrandRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "品牌编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "B00000001")
    @ExcelProperty("品牌编号")
    private String code;

    @Schema(description = "品牌名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "华为")
    @ExcelProperty("品牌名称")
    private String name;

    @Schema(description = "关联商品分类编号", example = "3200201")
    private Long categoryId;

    @Schema(description = "关联商品分类名称", example = "防腐涂料")
    @ExcelProperty("关联商品分类")
    private String categoryName;

    @Schema(description = "主要供应商编号", example = "3200101")
    private Long supplierId;

    @Schema(description = "主要供应商名称", example = "江苏鑫盛防腐涂料有限公司")
    @ExcelProperty("主要供应商")
    private String supplierName;

    @Schema(description = "品牌说明", example = "国际知名防腐涂料品牌")
    @ExcelProperty("品牌说明")
    private String description;

    @Schema(description = "资质文件地址，多个以逗号分隔")
    @ExcelProperty("资质文件")
    private String qualificationFiles;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
