package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 问题项复核入参（确认/忽略/标记已整改）。 */
@Data
public class BidReviewIssueReviewReqVO {

    @NotNull(message = "问题项不能为空")
    private Long id;
    @Schema(description = "复核状态：CONFIRMED/IGNORED/FIXED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "复核状态不能为空")
    private String status;
    @Schema(description = "复核意见")
    private String reviewRemark;
}
