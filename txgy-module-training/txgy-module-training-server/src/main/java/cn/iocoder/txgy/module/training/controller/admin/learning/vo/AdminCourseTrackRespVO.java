package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 单课程学习轨迹 Response VO")
@Data
public class AdminCourseTrackRespVO {

    @Schema(description = "学员用户 ID", example = "1")
    private Long userId;

    @Schema(description = "学员昵称", example = "张三")
    private String nickname;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String courseTitle;

    @Schema(description = "应获学时合计（小时）", example = "2.00")
    private BigDecimal creditTotal;

    @Schema(description = "累计有效学习秒数", example = "3600")
    private Integer totalValidSeconds;

    @Schema(description = "累计已获学时（小时）", example = "1.20")
    private BigDecimal creditEarned;

    @Schema(description = "学习任务状态：0 待学习 1 学习中 2 已完成", example = "1")
    private Integer taskStatus;

    @Schema(description = "媒资学习明细")
    private List<MediaTrack> mediaTracks;

    @Schema(description = "媒资学习轨迹")
    @Data
    public static class MediaTrack {

        @Schema(description = "媒资 ID", example = "11")
        private Long mediaId;

        @Schema(description = "媒资类型：video/document", example = "video")
        private String mediaType;

        @Schema(description = "媒资类型名", example = "视频")
        private String mediaTypeName;

        @Schema(description = "媒资名称", example = "第一章 课程介绍")
        private String name;

        @Schema(description = "学习会话数", example = "2")
        private Integer sessionCount;

        @Schema(description = "有效学习秒数", example = "1800")
        private Integer totalValidSeconds;

        @Schema(description = "已完成", example = "true")
        private Boolean completed;

        @Schema(description = "进度（%）", example = "80.00")
        private BigDecimal ratioPercent;

        @Schema(description = "已获学时（小时）", example = "0.80")
        private BigDecimal creditEarned;

        @Schema(description = "会话明细")
        private List<SessionTrack> sessions;

    }

    @Schema(description = "学习会话明细")
    @Data
    public static class SessionTrack {

        @Schema(description = "会话 ID", example = "1001")
        private Long sessionId;

        @Schema(description = "开始时间")
        private LocalDateTime startAt;

        @Schema(description = "结束时间")
        private LocalDateTime endAt;

        @Schema(description = "有效学习秒数", example = "900")
        private Integer validSeconds;

        @Schema(description = "结束原因", example = "normal")
        private String endReason;

        @Schema(description = "该会话折算学时（小时）", example = "0.40")
        private BigDecimal creditEarned;

    }

}
