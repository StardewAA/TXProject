package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "学员端 - 开启学习会话 Request VO")
@Data
public class LearnerSessionStartReqVO {

    @Schema(description = "课程 ID（直播/回放独立学习时为空）", example = "1")
    private Long courseId;

    @Schema(description = "媒资 ID：课程媒资 ID 或直播房间 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "媒资 ID 不能为空")
    private Long mediaId;

    @Schema(description = "学习媒资类型：video / document / live / replay", requiredMode = Schema.RequiredMode.REQUIRED, example = "video")
    @NotEmpty(message = "学习媒资类型不能为空")
    private String mediaType;

}
