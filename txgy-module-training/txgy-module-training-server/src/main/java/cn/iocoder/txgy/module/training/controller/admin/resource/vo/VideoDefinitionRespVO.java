package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 视频清晰度选项 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoDefinitionRespVO {

    @Schema(description = "清晰度：2160P / 1080P / 720P / 480P / 360P", example = "720P")
    private String definition;

    @Schema(description = "该清晰度的播放地址", example = "/admin-api/training/video/play?id=1")
    private String url;

    @Schema(description = "是否已完成转码（false 表示暂按原始清晰度播放）", example = "false")
    private Boolean transcoded;

    @Schema(description = "是否为上传时的原始清晰度", example = "true")
    private Boolean original;

}
