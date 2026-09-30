package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 风险规则分页入参（前端 RulePageReq：ruleName/enabled/riskLevel）。 */
@Schema(description = "管理后台 - 安许风险规则分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class SafetyRiskRulePageReqVO extends PageParam {

    @Schema(description = "规则名称（模糊）", example = "安许")
    private String ruleName;

    @Schema(description = "启用/禁用（可空=全部）", example = "true")
    private Boolean enabled;

    @Schema(description = "命中等级 code（可空=全部）", example = "HIGH")
    private String riskLevel;
}
