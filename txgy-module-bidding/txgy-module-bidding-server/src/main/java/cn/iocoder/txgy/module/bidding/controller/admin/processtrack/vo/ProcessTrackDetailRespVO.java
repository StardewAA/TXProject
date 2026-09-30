package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口4
// 详情 = ProcessTrackItemVO + events[]（操作记录事件史，只读）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 投标流程跟踪详情（含事件史）")
@Data
public class ProcessTrackDetailRespVO {

    @Schema(description = "总览项字段（同 page 项）")
    private ProcessTrackItemVO item;

    @Schema(description = "操作记录事件史（只读展示，按时间升序）")
    private List<ProcessTrackEventVO> events;
}
