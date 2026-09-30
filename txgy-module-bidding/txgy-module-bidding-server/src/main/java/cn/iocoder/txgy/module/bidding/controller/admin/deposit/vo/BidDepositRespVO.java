package cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 投标保证金 Response")
@Data
public class BidDepositRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "投标主体ID")
    private Long subjectId;

    @Schema(description = "保证金金额（元）")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currencyCode;

    @Schema(description = "缴纳方式")
    private String payType;

    @Schema(description = "缴纳状态")
    private String status;

    @Schema(description = "缴纳凭证文件ID")
    private Long fileId;

    @Schema(description = "缴纳时间")
    private LocalDateTime depositTime;

    @Schema(description = "退还时间")
    private LocalDateTime refundTime;

    @Schema(description = "退还状态")
    private String refundStatus;

    @Schema(description = "是否超期未退还（条例第58条：合同签订后5日内退还）")
    private Boolean refundOverdue;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
