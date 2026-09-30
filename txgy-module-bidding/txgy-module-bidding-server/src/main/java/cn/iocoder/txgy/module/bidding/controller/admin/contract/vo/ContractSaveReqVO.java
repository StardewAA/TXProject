package cn.iocoder.txgy.module.bidding.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工程合同登记 Request（登记时校验履约保证金 10% 与签约 30 日）")
@Data
public class ContractSaveReqVO {

    @Schema(description = "招标项目编号（TENDER_PROJECT_CODE）", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001001")
    @NotNull(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（BID_SECTION_CODE，空=项目级）")
    private String bidSectionCode;

    @Schema(description = "合同编号（内部编号，前缀 C）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "合同编号不能为空")
    private String contractCode;

    @Schema(description = "合同名称")
    private String contractName;

    @Schema(description = "合同价（元）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000000.00")
    @NotNull(message = "合同价不能为空")
    private BigDecimal contractPrice;

    @Schema(description = "履约保证金（元）", example = "100000.00")
    private BigDecimal performanceBond;

    @Schema(description = "中标通知书发出时间（签约 30 日时限起算点）")
    private LocalDateTime winNoticeTime;

    @Schema(description = "合同签订时间")
    private LocalDateTime signTime;

    @Schema(description = "合同文件 FileApi 主键（电子签名文件）")
    private Long fileId;

    @Schema(description = "合同状态（SIGNED 已签 / ARCHIVED 已归档）")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
