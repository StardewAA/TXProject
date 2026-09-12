package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 资源分类树节点 Response VO")
@Data
public class CategoryTreeRespVO {

    @Schema(description = "分类 ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类编码，用于跨类型同步")
    private String code;

    @Schema(description = "父分类 ID（0=根）")
    private Long parentId;

    @Schema(description = "层级（1=一级 / 2=二级 / 3=三级）")
    private Integer level;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "子分类（无则空数组）")
    private List<CategoryTreeRespVO> children = new ArrayList<>();

}
