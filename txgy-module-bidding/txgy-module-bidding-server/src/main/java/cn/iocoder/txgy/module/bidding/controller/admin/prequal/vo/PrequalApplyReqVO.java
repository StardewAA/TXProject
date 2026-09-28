package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 资格预审申请提交 Request（投标方）")
@Data
public class PrequalApplyReqVO {

    @Schema(description = "资格预审ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "资格预审ID不能为空")
    private Long prequalId;

    @Schema(description = "投标主体ID（bidding_subject.id）")
    private Long subjectId;

    @Schema(description = "资格预审申请文件 FileApi 主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请文件不能为空")
    private Long fileId;

    @Schema(description = "备注")
    private String remark;
}
