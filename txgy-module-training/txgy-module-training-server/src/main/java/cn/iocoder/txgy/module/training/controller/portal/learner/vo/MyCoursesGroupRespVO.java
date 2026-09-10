package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "学员端 - 我的课程（分组）Response VO")
@Data
public class MyCoursesGroupRespVO {

    @Schema(description = "待学习课程")
    private List<LearnerCourseCardRespVO> todoCourses;

    @Schema(description = "学习中课程")
    private List<LearnerCourseCardRespVO> learningCourses;

    @Schema(description = "已完成课程")
    private List<LearnerCourseCardRespVO> completedCourses;

    @Schema(description = "课程总数", example = "5")
    private Integer totalCount;

    @Schema(description = "已完成课程数", example = "2")
    private Integer completedCount;

    @Schema(description = "学习中课程数", example = "1")
    private Integer learningCount;

    @Schema(description = "待学习课程数", example = "2")
    private Integer todoCount;

    @Schema(description = "应获学时合计（小时）", example = "10.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时合计（小时）", example = "3.50")
    private BigDecimal creditEarned;

}
