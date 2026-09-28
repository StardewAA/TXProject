package cn.iocoder.txgy.module.bidding.controller.admin.review.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审查通过 Request VO（契约 §5 接口 4：taskId/version）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§4/§5。
 * <p>为什么 `version` 必填：两个审查员同时打开同一任务时，后提交者若不校验版本就会**覆盖**先提交者的结论；
 * 故版本号由前端从清单行带回，服务端 CAS 校验，冲突报错让用户刷新重看（宁可重看，也不静默改结论）。
 *
 * @author txgy
 */
@Schema(description = "审查通过 Request VO")
@Data
public class ReviewTaskApproveReqVO {

    @Schema(description = "任务 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务 id 不能为空")
    private Long taskId;

    @Schema(description = "乐观锁版本（来自清单/详情）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "版本号不能为空")
    private Integer version;

}
