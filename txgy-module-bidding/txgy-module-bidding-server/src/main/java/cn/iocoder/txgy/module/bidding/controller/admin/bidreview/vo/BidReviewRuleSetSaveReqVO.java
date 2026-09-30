package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/** 规则集新增/修改入参。 */
@Data
public class BidReviewRuleSetSaveReqVO {

    private Long id;
    @Schema(description = "规则集编码（留空自动生成）")
    private String ruleSetCode;
    @NotEmpty(message = "规则集名称不能为空")
    private String ruleSetName;
    @Schema(description = "来源：PLATFORM/TENDER_DOC/MIXED")
    private String source = "PLATFORM";
    private Boolean enable = true;
    @Schema(description = "是否平台默认规则集（个人端未传招标文件时使用）")
    private Boolean defaultFlag = false;
    private String remark;
}
