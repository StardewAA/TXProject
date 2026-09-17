package cn.iocoder.txgy.module.bidding.controller.admin.filing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备案提交出参（BID-ENT-REG-01：受理回执）。
 */
@Schema(description = "备案提交出参（BID-ENT-REG-01）")
@Data
public class FilingSubmitRespVO {

    @Schema(description = "备案受理号（服务端生成）")
    private String receiptNo;
    @Schema(description = "受理状态：PENDING")
    private String status;
    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

}
