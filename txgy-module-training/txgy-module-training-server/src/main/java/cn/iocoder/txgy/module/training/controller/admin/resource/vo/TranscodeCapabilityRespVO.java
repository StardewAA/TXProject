package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 视频转码能力 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodeCapabilityRespVO {

    @Schema(description = "是否开启转码", example = "true")
    private Boolean enabled;

    @Schema(description = "ffmpeg 是否可用", example = "true")
    private Boolean ffmpegAvailable;

    @Schema(description = "ffmpeg 版本信息")
    private String ffmpegVersion;

    @Schema(description = "转码目标清晰度", example = "[\"360P\",\"480P\",\"720P\"]")
    private List<String> targetDefinitions;

    @Schema(description = "转码并发数", example = "2")
    private Integer poolSize;

}
