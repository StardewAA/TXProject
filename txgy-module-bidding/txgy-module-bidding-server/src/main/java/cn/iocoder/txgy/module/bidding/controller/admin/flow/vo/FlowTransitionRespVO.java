package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 招标全流程状态流转 Response VO")
@Data
public class FlowTransitionRespVO {

    @Schema(description = "流转实体类型", example = "PROJECT")
    private String bizType;

    @Schema(description = "业务编号", example = "I6101002026000001")
    private String bizCode;

    @Schema(description = "流转前状态", example = "DRAFT")
    private String fromState;

    @Schema(description = "流转后状态", example = "APPROVING")
    private String toState;

    @Schema(description = "流转事件", example = "SUBMIT_APPROVAL")
    private String event;
}
