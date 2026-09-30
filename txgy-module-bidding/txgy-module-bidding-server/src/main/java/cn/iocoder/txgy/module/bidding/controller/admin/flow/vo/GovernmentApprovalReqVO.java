package cn.iocoder.txgy.module.bidding.controller.admin.flow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 政府审批回调 Request VO（预留接口）")
@Data
public class GovernmentApprovalReqVO {

    @Schema(description = "审批对象类型：PROJECT 项目立项 / TENDER_PROJECT 招标方案 / TERMINATION 终止审批 / ANNOUNCEMENT 公告审核",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJECT")
    @NotBlank(message = "审批对象类型（bizType）不能为空")
    private String bizType;

    @Schema(description = "审批对象业务标识（项目/招标项目/公告 id 或业务编号）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2100016652328599554")
    @NotBlank(message = "审批对象业务标识（bizId）不能为空")
    private String bizId;

    @Schema(description = "审批动作：APPROVE 通过 / REJECT 驳回",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVE")
    @NotBlank(message = "审批动作（action）不能为空")
    private String action;

    @Schema(description = "审批人（政府侧账号）", example = "监管岗-张工")
    private String operator;

    @Schema(description = "审批意见", example = "同意")
    private String remark;
}
