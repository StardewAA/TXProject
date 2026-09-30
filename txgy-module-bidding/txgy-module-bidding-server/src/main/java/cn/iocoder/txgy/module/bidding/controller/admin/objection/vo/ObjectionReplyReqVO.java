package cn.iocoder.txgy.module.bidding.controller.admin.objection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 异议答复 Request（收到后 3 日内答复，答复前暂停）")
@Data
public class ObjectionReplyReqVO {

    @Schema(description = "异议记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "7301")
    @NotNull(message = "异议记录ID不能为空")
    private Long objectionId;

    @Schema(description = "答复内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "答复内容不能为空")
    private String replyContent;
}
