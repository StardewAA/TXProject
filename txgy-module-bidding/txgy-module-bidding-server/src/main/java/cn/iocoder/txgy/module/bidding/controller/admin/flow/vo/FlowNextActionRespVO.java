package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 招标全流程可执行动作 Response VO")
@Data
public class FlowNextActionRespVO {

    @Schema(description = "流转事件 code", example = "SUBMIT_APPROVAL")
    private String event;

    @Schema(description = "事件中文名", example = "提交审批")
    private String label;

    @Schema(description = "目标状态", example = "APPROVING")
    private String toState;
}
