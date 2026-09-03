package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台/学员端 - 课程评论分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCommentPageReqVO extends PageParam {

    @Schema(description = "课程 ID（评论治理可空，查询全部）", example = "1")
    private Long courseId;

    @Schema(description = "父评论 ID（0 = 一级评论）", example = "0")
    private Long parentId;

    @Schema(description = "评论状态：1 正常 2 已删除（管理端不传=全部，学员端传 1）", example = "1")
    private Integer status;

}
