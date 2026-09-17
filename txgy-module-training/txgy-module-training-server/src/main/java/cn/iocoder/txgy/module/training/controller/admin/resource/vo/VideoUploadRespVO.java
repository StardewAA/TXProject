package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 视频异步上传 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadRespVO {

    @Schema(description = "视频资源 ID", example = "1024")
    private Long videoId;

    @Schema(description = "是否已立即返回（异步处理中）", example = "true")
    private Boolean async;

    @Schema(description = "生成的转码清晰度任务数", example = "3")
    private Integer taskCount;

    @Schema(description = "转码目标清晰度", example = "[\"360P\",\"480P\",\"720P\"]")
    private List<String> targetDefinitions;

    @Schema(description = "提示信息")
    private String message;

}
