package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 培训班随机收录学员 Response VO")
@Data
public class ClassStudentRandomAddRespVO {

    @Schema(description = "本次实际收录人数", example = "10")
    private Integer addedCount;

    @Schema(description = "命中人员总数", example = "36")
    private Integer matchedCount;

    @Schema(description = "可收录人数（命中人员 - 已在班）", example = "32")
    private Integer addableCount;

    @Schema(description = "收录后在班人数", example = "42")
    private Integer inClassCount;

    @Schema(description = "在班学员上限", example = "50")
    private Integer maxSize;

    @Schema(description = "收录后剩余名额", example = "8")
    private Integer remaining;

    @Schema(description = "本次随机收录名单")
    private List<User> users;

    @Schema(description = "本次随机收录名单")
    @Data
    public static class User {

        @Schema(description = "用户 ID", example = "3001")
        private Long userId;

        @Schema(description = "用户昵称", example = "小王")
        private String nickname;

        @Schema(description = "部门/班组名称", example = "管道一班")
        private String deptName;

    }

}
