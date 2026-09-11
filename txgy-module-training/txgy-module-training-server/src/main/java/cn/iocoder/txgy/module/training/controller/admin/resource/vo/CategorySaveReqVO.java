package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "管理后台 - 资源分类 新增/修改 Request VO")
@Data
public class CategorySaveReqVO {

    @Schema(description = "分类ID（修改时必传）")
    private Long id;

    @Schema(description = "分类类型：doc 文档 / video 视频 / question 试题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "分类类型不能为空")
    private String type;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "分类名称不能为空")
    private String name;

    @Schema(description = "父分类 ID（0=根，仅支持二级）")
    private Long parentId;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否启用：true 启用 false 停用")
    private Boolean status;

}
