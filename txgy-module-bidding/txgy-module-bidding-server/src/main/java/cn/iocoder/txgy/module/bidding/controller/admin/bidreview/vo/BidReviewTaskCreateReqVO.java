package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理端建单入参（可指定归属人、规则集、招标文件与标书）。 */
@Data
public class BidReviewTaskCreateReqVO {

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务名称不能为空")
    private String taskName;
    @Schema(description = "归属人（留空则归当前登录人）")
    private Long ownerUserId;
    @Schema(description = "规则集 id（留空则用平台默认规则集）")
    private Long ruleSetId;
    @Schema(description = "招标文件 file_id")
    private Long tenderFileId;
    private String tenderFileUrl;
    private String tenderFileName;
    @Schema(description = "投标文件（标书）file_id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请先上传投标文件")
    private Long bidFileId;
    private String bidFileUrl;
    private String bidFileName;
    private Long projectId;
    private Long tenderProjectId;
    private String remark;
    @Schema(description = "是否立即生成（默认 true）")
    private Boolean generate = true;
}
