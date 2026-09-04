package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 培训班课程清单 Response VO")
@Data
public class ClassCourseRespVO {

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "课程编码", example = "TC1")
    private String courseCode;

    @Schema(description = "课程标题", example = "管道防腐施工工艺")
    private String courseTitle;

    @Schema(description = "课程总时长（分钟）", example = "120")
    private Integer durationMinutes;

}
