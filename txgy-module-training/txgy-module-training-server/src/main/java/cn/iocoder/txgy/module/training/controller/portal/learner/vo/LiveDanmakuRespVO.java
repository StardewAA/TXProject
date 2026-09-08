package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "学员端 - 直播弹幕 Response VO")
@Data
public class LiveDanmakuRespVO {

    @Schema(description = "弹幕 ID", example = "1")
    private Long id;

    @Schema(description = "直播房间 ID", example = "1")
    private Long roomId;

    @Schema(description = "发送人用户 ID", example = "1")
    private Long userId;

    @Schema(description = "发送人昵称", example = "张三")
    private String userName;

    @Schema(description = "弹幕内容", example = "老师讲得真好")
    private String content;

    @Schema(description = "发送时间")
    private LocalDateTime createAt;

}
