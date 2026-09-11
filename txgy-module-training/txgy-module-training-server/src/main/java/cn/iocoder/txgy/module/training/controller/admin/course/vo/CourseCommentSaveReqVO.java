package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台/学员端 - 课程评论发表 Request VO")
@Data
public class CourseCommentSaveReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "父评论 ID（0 = 一级评论）", example = "0")
    private Long parentId;

    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "课程讲解很清晰，推荐！")
    @NotEmpty(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过 500 个字符")
    private String content;

}
