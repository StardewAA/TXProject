package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件台账新增/修改 Request VO")
@Data
public class HrmCertificateLedgerSaveReqVO {

    @Schema(description = "证件台账编号", example = "1024")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Schema(description = "证件类型编号", example = "1")
    private Long typeId;

    @Schema(description = "证件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "低压电工作业操作证")
    @NotBlank(message = "证件名称不能为空")
    @Size(max = 255, message = "证件名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "证件级别", example = "初级")
    @Size(max = 255, message = "证件级别长度不能超过 255 个字符")
    private String level;

    @Schema(description = "证件编号", example = "T44030019900718001X")
    @Size(max = 255, message = "证件编号长度不能超过 255 个字符")
    private String no;

    @Schema(description = "有效开始日期")
    private LocalDateTime startTime;

    @Schema(description = "有效结束日期")
    private LocalDateTime endTime;

    @Schema(description = "复审日期")
    private LocalDateTime reviewTime;

    @Schema(description = "发证机构", example = "深圳市应急管理局")
    @Size(max = 255, message = "发证机构长度不能超过 255 个字符")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDateTime issuingTime;

    @Schema(description = "证件附件地址")
    @Size(max = 512, message = "证件附件地址长度不能超过 512 个字符")
    private String attachmentUrl;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "显示顺序", example = "10")
    @Min(value = 0, message = "显示顺序不能小于 0")
    private Integer sort;

}
