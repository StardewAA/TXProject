package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 政府审批回调 Response VO（预留接口）")
@Data
public class GovernmentApprovalRespVO {

    @Schema(description = "是否批准（true=通过）", example = "true")
    private Boolean approved;

    @Schema(description = "审批文号（预留 stub 模拟生成；真实审批系统返回后回填）", example = "GOV-STUB-20260920103000-PROJECT-xxx")
    private String approvalNo;

    @Schema(description = "审批时间")
    private LocalDateTime approvedTime;

    @Schema(description = "审批意见/结果说明", example = "预留接口 stub：审批通过")
    private String message;

    @Schema(description = "是否预留 stub（true=当前为预留模拟实现）", example = "true")
    private Boolean stub;
}
