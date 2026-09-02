package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 课程分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CoursePageReqVO extends PageParam {

    @Schema(description = "课程标题（模糊）", example = "防腐")
    private String title;

    @Schema(description = "课程编码（模糊）", example = "TC-")
    private String code;

    @Schema(description = "课程分类 ID", example = "101")
    private Long categoryId;

    @Schema(description = "状态：0 草稿 1 待审核 2 审核通过 3 已上架 4 已下架", example = "3")
    private Integer status;

    @Schema(description = "主讲教师", example = "张工")
    private String lecturer;

    @Schema(description = "是否当前生效版本", example = "true")
    private Boolean isCurrent;

}
