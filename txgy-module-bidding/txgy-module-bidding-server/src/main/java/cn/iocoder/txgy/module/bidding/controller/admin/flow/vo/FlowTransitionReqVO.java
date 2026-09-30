package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 招标全流程状态流转 Request VO")
@Data
public class FlowTransitionReqVO {

    @Schema(description = "流转实体类型：PROJECT/TENDER_PROJECT/BID_SECTION", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJECT")
    @NotBlank(message = "流转实体类型（bizType）不能为空")
    private String bizType;

    @Schema(description = "业务编号（project_code/tender_project_code/bid_section_code）", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001")
    @NotBlank(message = "业务编号（bizCode）不能为空")
    private String bizCode;

    @Schema(description = "流转事件（FlowTransitionEventEnum code；测试模式=下一步）", requiredMode = Schema.RequiredMode.REQUIRED, example = "SUBMIT_APPROVAL")
    @NotBlank(message = "流转事件（event）不能为空")
    private String event;

    @Schema(description = "流转说明/审批意见", example = "同意立项")
    private String remark;
}
