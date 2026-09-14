package cn.iocoder.txgy.module.bidding.controller.admin.filing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 备案提交入参（05 §2.1/§3.1 BID-ENT-REG-01）。
 * 幂等键 = 备案类型(filingType) + 项目编号(projectCode)；重复提交返回"已提交"。
 */
@Schema(description = "备案提交入参（BID-ENT-REG-01）")
@Data
public class FilingSubmitReqVO {

    @Schema(description = "备案类型", example = "NOTICE_FILING")
    @NotBlank(message = "备案类型不能为空")
    private String filingType;

    @Schema(description = "项目编号（幂等键②）")
    @NotBlank(message = "项目编号不能为空")
    private String projectCode;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "统一交易标识码")
    private String unifiedDealCode;

    @Schema(description = "招标方式（代码）")
    private String tenderMode;

    @Schema(description = "组织形式（代码）")
    private String orgForm;

    @Schema(description = "监督部门代码")
    private String superviseDeptCode;

    @Schema(description = "监督部门名称")
    private String superviseDeptName;

    @Schema(description = "提交时间（缺省取当前时间）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime submitTime;

    @Schema(description = "data_timestamp（05 交换口径）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime dataTimestamp;

    @Schema(description = "附件 file_id 列表（快照存 extra_json）")
    private List<Long> attachmentFileIds;

}
