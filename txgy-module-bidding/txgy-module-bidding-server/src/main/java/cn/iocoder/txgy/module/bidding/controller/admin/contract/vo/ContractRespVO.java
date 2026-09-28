package cn.iocoder.txgy.module.bidding.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工程合同 Response")
@Data
public class ContractRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "合同编号")
    private String contractCode;

    @Schema(description = "合同名称")
    private String contractName;

    @Schema(description = "合同价（元）")
    private BigDecimal contractPrice;

    @Schema(description = "履约保证金（元）")
    private BigDecimal performanceBond;

    @Schema(description = "中标通知书发出时间")
    private LocalDateTime winNoticeTime;

    @Schema(description = "合同签订时间")
    private LocalDateTime signTime;

    @Schema(description = "合同文件 FileApi 主键")
    private Long fileId;

    @Schema(description = "合同状态（SIGNED 已签 / ARCHIVED 已归档）")
    private String status;

    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;

    @Schema(description = "保存到期时间（归档 + 15 年，自动计算）")
    private LocalDateTime retentionUntil;

    @Schema(description = "保存期限提醒标记（NORMAL 正常 / EXPIRING_SOON 临期 / EXPIRED 已到期）")
    private String retentionWarn;

    @Schema(description = "合同一致性比对结果（MATCH 与中标结果一致 / MISMATCH 不一致标红 / NO_BASELINE 无中标基准）")
    private String compareResult;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
