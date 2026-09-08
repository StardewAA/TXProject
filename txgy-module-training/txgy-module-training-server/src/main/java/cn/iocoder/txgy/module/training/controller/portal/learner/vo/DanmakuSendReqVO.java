package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "学员端 - 发送直播弹幕 Request VO")
@Data
public class DanmakuSendReqVO {

    @Schema(description = "直播房间 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "直播房间不能为空")
    private Long roomId;

    @Schema(description = "弹幕内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "老师讲得真好")
    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 200, message = "弹幕内容不能超过 200 字")
    private String content;

}
