package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 视频转码任务 Response VO")
@Data
public class VideoTranscodeTaskRespVO {

    @Schema(description = "任务 ID", example = "1")
    private Long id;

    @Schema(description = "视频资源 ID", example = "1024")
    private Long videoId;

    @Schema(description = "目标清晰度", example = "720P")
    private String definition;

    @Schema(description = "目标画面高度", example = "720")
    private Integer height;

    @Schema(description = "任务状态：0 待转码 1 转码中 2 成功 3 失败 4 跳过", example = "1")
    private Integer status;

    @Schema(description = "任务状态名称", example = "转码中")
    private String statusName;

    @Schema(description = "进度百分比（0-100）", example = "60")
    private Integer progress;

    @Schema(description = "输出文件大小（字节）", example = "10485760")
    private Long fileSize;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

}
