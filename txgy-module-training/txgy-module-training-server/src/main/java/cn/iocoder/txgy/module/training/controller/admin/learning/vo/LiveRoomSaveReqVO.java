package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 直播房间新增/修改 Request VO")
@Data
public class LiveRoomSaveReqVO {

    @Schema(description = "房间 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "直播标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "现场安全管理规范解读")
    @NotEmpty(message = "直播标题不能为空")
    private String title;

    @Schema(description = "封面图 URL", example = "https://...")
    private String coverUrl;

    @Schema(description = "讲师姓名", example = "王讲师")
    private String lecturerName;

    @Schema(description = "直播流地址", example = "http://media.local/video/live-uat-v1.mp4")
    private String streamUrl;

    @Schema(description = "状态：0 未开始 1 直播中 2 已结束", example = "0")
    private Integer status;

    @Schema(description = "计划开始时间", example = "2026-09-10 14:00:00")
    private LocalDateTime planStartAt;

    @Schema(description = "计划结束时间", example = "2026-09-10 15:00:00")
    private LocalDateTime planEndAt;

    @Schema(description = "回放视频地址", example = "http://media.local/video/replay-uat-v1.mp4")
    private String replayVideoUrl;

    @Schema(description = "讲师课时（小时）", example = "1.00")
    private BigDecimal creditHours;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "备注", example = "内测演示直播")
    private String remark;

}
