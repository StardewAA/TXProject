package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 学习任务指派候选数据 Response VO")
@Data
public class AssignCandidateRespVO {

    @Schema(description = "可选课程列表（已上架且当前生效版本）")
    private List<CourseItem> courses;

    @Schema(description = "可指派学员数量（由前端用户选择）")
    private Long userCount;

    @Schema(description = "已有学习任务的学员（含编号与昵称，便于前端回显）")
    private List<UserItem> users;

    @Schema(description = "学员选项项")
    @Data
    public static class UserItem {

        @Schema(description = "学员用户 ID", example = "1")
        private Long id;

        @Schema(description = "学员昵称", example = "张三")
        private String nickname;

    }

    @Schema(description = "课程选项项")
    @Data
    public static class CourseItem {

        @Schema(description = "课程 ID", example = "1")
        private Long id;

        @Schema(description = "课程标题", example = "管道防腐保温")
        private String title;

        @Schema(description = "课程学分", example = "2.00")
        private BigDecimal credit;

        @Schema(description = "课程总时长（分钟）", example = "45")
        private Integer durationMinutes;

        @Schema(description = "封面图", example = "https://...")
        private String coverUrl;

        @Schema(description = "讲师", example = "王讲师")
        private String lecturer;

    }

}
