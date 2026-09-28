package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 安许风险条目一键预警入参（五页改造缺接口契约 Q22，2026-09-28）。
 *
 * <p>规范出处：Q22 契约——对所选风险条目批量发预警（幂等，同日同条不重复推送）。
 */
@Schema(description = "管理后台 - 安许风险条目一键预警入参")
@Data
public class SafetyRiskItemAlertBatchReqVO {

    @Schema(description = "风险条目 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "风险条目不能为空")
    private List<Long> ids;
}
