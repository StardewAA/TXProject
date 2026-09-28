package cn.iocoder.txgy.module.bidding.controller.admin.review.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审查驳回 Request VO（契约 §5 接口 5：taskId/version/reason 必填）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§3/§5 —— 驳回原因必填留痕且**租户可见**。
 * <p>为什么 reason 必填：驳回是"企业要据此整改"的唯一信息载体，没有原因的驳回等于让企业猜；
 * 且审查结论需可追溯（谁、何时、因何驳回）。故此处硬校验，且长度上限与库表一致（512）。
 *
 * @author txgy
 */
@Schema(description = "审查驳回 Request VO")
@Data
public class ReviewTaskRejectReqVO {

    @Schema(description = "任务 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务 id 不能为空")
    private Long taskId;

    @Schema(description = "乐观锁版本（来自清单/详情）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Schema(description = "驳回原因（必填，租户可见）", requiredMode = Schema.RequiredMode.REQUIRED, example = "证书影像不清晰，请重新上传")
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 512, message = "驳回原因长度不能超过 512")
    private String reason;

}
