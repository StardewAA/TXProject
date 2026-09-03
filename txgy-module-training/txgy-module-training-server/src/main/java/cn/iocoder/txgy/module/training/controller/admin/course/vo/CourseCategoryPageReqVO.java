package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 课程分类分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCategoryPageReqVO extends PageParam {

    @Schema(description = "分类名称（模糊）", example = "防腐")
    private String name;

    @Schema(description = "分类编码", example = "anticorrosion-insulation")
    private String code;

}
