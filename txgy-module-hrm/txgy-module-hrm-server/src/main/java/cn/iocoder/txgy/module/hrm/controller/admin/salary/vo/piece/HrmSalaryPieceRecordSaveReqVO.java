package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - HRM 计件计量明细保存 Request VO")
@Data
public class HrmSalaryPieceRecordSaveReqVO {

    @Schema(description = "明细编号（为空为新增）", example = "1")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Schema(description = "年", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026")
    @NotNull(message = "年不能为空")
    private Integer year;

    @Schema(description = "月", requiredMode = Schema.RequiredMode.REQUIRED, example = "9")
    @NotNull(message = "月不能为空")
    @Min(value = 1, message = "月不能小于 1")
    @Max(value = 12, message = "月不能大于 12")
    private Integer month;

    @Schema(description = "工程量/工序名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "管道保温-岩棉")
    @NotBlank(message = "工程量/工序名称不能为空")
    @Size(max = 100, message = "工程量/工序名称不能超过 100 个字符")
    private String itemName;

    @Schema(description = "单位", example = "㎡")
    @Size(max = 20, message = "单位不能超过 20 个字符")
    private String unit;

    @Schema(description = "完成工程量", requiredMode = Schema.RequiredMode.REQUIRED, example = "320")
    @NotNull(message = "完成工程量不能为空")
    @DecimalMin(value = "0", message = "完成工程量不能小于 0")
    private BigDecimal quantity;

    @Schema(description = "计件单价", requiredMode = Schema.RequiredMode.REQUIRED, example = "45")
    @NotNull(message = "计件单价不能为空")
    @DecimalMin(value = "0", message = "计件单价不能小于 0")
    private BigDecimal unitPrice;

    @Schema(description = "备注", example = "2# 锅炉房管道")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
