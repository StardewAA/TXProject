package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 培训班学员「手动指定培训班入班」结果 Response VO
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员手动指定培训班入班结果 Response VO")
@Data
public class ClassStudentImportAllocateRespVO {

    @Schema(description = "实际入班人次（已在班自动跳过，不计入）", example = "28")
    private Integer successCount;

    @Schema(description = "单班在班学员上限", example = "50")
    private Integer maxSize;

    @Schema(description = "各培训班的入班结果")
    private List<ClassResult> classResults;

    @Schema(description = "单个培训班的入班结果")
    @Data
    public static class ClassResult {

        @Schema(description = "培训班 ID", example = "1")
        private Long classId;

        @Schema(description = "培训班名称", example = "2024 年管道焊工岗前培训班")
        private String className;

        @Schema(description = "入班前在班人数", example = "42")
        private Integer beforeCount;

        @Schema(description = "本次实际入班人数", example = "8")
        private Integer addedCount;

        @Schema(description = "入班后在班人数", example = "50")
        private Integer afterCount;

        @Schema(description = "入班后剩余名额", example = "0")
        private Integer remaining;

        @Schema(description = "本次入班学员名单")
        private List<User> users;

    }

    @Schema(description = "入班学员")
    @Data
    public static class User {

        @Schema(description = "用户 ID", example = "3001")
        private Long userId;

        @Schema(description = "学员姓名", example = "小王")
        private String userName;

        @Schema(description = "部门/班组名称", example = "管道一班")
        private String deptName;

    }

}
