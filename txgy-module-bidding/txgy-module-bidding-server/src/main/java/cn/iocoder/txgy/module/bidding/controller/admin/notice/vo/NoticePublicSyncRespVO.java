package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 公开同步出参（BID-SUP-GATHER-02）。
 */
@Schema(description = "公开同步出参（BID-SUP-GATHER-02）")
@Data
public class NoticePublicSyncRespVO {

    @Schema(description = "同步任务编号")
    private String taskNo;
    @Schema(description = "同步状态：SUCCESS / REJECTED（白名单校验不通过）")
    private String status;

}
