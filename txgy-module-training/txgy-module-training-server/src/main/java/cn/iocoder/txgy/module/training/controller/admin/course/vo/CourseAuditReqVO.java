package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 课程审核操作 Request VO（通过/驳回共用）")
@Data
public class CourseAuditReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "审核意见（驳回时必填）", example = "简介缺少安全规范章节，请补充")
    @Size(max = 500, message = "审核意见长度不能超过 500 个字符")
    private String reason;

}
