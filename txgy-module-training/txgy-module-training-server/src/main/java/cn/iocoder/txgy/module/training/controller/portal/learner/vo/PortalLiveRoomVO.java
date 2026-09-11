package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "学员端 - 直播房间 Response VO")
@Data
public class PortalLiveRoomVO {

    @Schema(description = "房间 ID", example = "1")
    private Long id;

    @Schema(description = "直播标题", example = "现场安全管理规范解读")
    private String title;

    @Schema(description = "封面图 URL", example = "https://...")
    private String coverUrl;

    @Schema(description = "讲师姓名", example = "王讲师")
    private String lecturerName;

    @Schema(description = "状态：0 未开始 1 直播中 2 已结束", example = "1")
    private Integer status;

    @Schema(description = "状态文案：未开始/直播中/已结束", example = "直播中")
    private String statusText;

    @Schema(description = "计划开始时间", example = "2026-09-10 14:00:00")
    private LocalDateTime planStartAt;

    @Schema(description = "计划结束时间", example = "2026-09-10 15:00:00")
    private LocalDateTime planEndAt;

    @Schema(description = "直播流地址（直播中可播放）", example = "http://media.local/video/live.mp4")
    private String streamUrl;

    @Schema(description = "回放视频地址（已结束可回放）", example = "http://media.local/video/replay.mp4")
    private String replayVideoUrl;

    @Schema(description = "讲师课时（小时）", example = "1.00")
    private BigDecimal creditHours;

    @Schema(description = "当前观看人数", example = "12")
    private Long viewerCount;

    @Schema(description = "是否可以进入直播", example = "true")
    private Boolean canJoin;

    @Schema(description = "是否可以观看回放", example = "false")
    private Boolean canReplay;

    @Schema(description = "备注", example = "内测演示直播")
    private String remark;

}
