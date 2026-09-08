package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 课程分类分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCategoryPageReqVO extends PageParam {

    @Schema(description = "父级分类 ID（0=顶级域）", example = "0")
    private Long parentId;

    @Schema(description = "层级：1 域 / 2 组 / 3 课", example = "1")
    private Integer level;

    @Schema(description = "分类名称（模糊）", example = "防腐")
    private String name;

    @Schema(description = "分类编码", example = "A01")
    private String code;

    @Schema(description = "状态：active 启用 / inactive 停用", example = "active")
    private String status;

}
