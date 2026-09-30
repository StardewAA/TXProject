package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 招标全流程流转留痕 Response VO")
@Data
public class FlowLogRespVO {

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

    @Schema(description = "操作人", example = "1")
    private String operator;

    @Schema(description = "操作 IP")
    private String operatorIp;

    @Schema(description = "流转说明/审批意见")
    private String remark;

    @Schema(description = "流转时间")
    private LocalDateTime createTime;
}
