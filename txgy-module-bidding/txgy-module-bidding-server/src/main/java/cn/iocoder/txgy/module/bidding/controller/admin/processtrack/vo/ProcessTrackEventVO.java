package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口4 events[]
// 事件史只读展示（页面无手动推进入口）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 投标流程事件史项（操作记录留痕）")
@Data
public class ProcessTrackEventVO {

    @Schema(description = "事件类型 code（CREATE/REGISTER/APPROVE/REJECT/UPLOAD_DOCUMENT/CANCEL）")
    private String eventType;
    @Schema(description = "事件中文名（前端优先用字典 bidding_process_event，回退用此列）")
    private String eventName;
    @Schema(description = "源状态")
    private String fromState;
    @Schema(description = "目标状态")
    private String toState;
    @Schema(description = "操作人名称（快照）")
    private String operatorName;
    @Schema(description = "操作描述")
    private String operationDesc;
    @Schema(description = "事件发生时间")
    private LocalDateTime occurredAt;
}
