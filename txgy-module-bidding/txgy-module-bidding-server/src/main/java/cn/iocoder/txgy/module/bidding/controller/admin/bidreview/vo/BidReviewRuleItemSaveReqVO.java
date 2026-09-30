package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 规则项新增/修改入参。 */
@Data
public class BidReviewRuleItemSaveReqVO {

    private Long id;
    @NotNull(message = "规则集不能为空")
    private Long ruleSetId;
    @Schema(description = "规则编码（留空自动生成 R+序号）")
    private String ruleCode;
    @NotEmpty(message = "规则名称不能为空")
    private String ruleName;
    @Schema(description = "类别：FORM/QUALIFICATION/RESPONSE/BUSINESS/TECH/PRICE")
    private String category = "FORM";
    @Schema(description = "类型：DISQUALIFY/QUALIFICATION/FORMAT/SCORING")
    private String ruleType = "FORMAT";
    @Schema(description = "检查方式：RULE 规则匹配 / AI 语义判断")
    private String checkMode = "AI";
    @Schema(description = "严重级别：HIGH/MEDIUM/LOW")
    private String severity = "MEDIUM";
    private BigDecimal scoreMax;
    private String requirement;
    private String evidenceHint;
    private String sourceRef;
    private Integer sort = 0;
    private Boolean enable = true;
}
