package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 时间驱动调度记录 Response")
@Data
public class FlowTimerRespVO {

    @Schema(description = "调度记录 ID")
    private Long id;
    @Schema(description = "实体类型：PROJECT/TENDER_PROJECT/BID_SECTION")
    private String bizType;
    @Schema(description = "业务编号")
    private String bizCode;
    @Schema(description = "时间轴事件：BIDDING_STARTED/BIDDING_ENDED")
    private String event;
    @Schema(description = "触发时间（用户配置，精确到秒）")
    private LocalDateTime triggerTime;
    @Schema(description = "状态：WAITING/DONE/SKIPPED")
    private String status;
    @Schema(description = "实际执行时间")
    private LocalDateTime executedTime;
    @Schema(description = "来源说明")
    private String remark;
}
