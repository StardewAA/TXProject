package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 计件计量明细 Response VO")
@Data
public class HrmSalaryPieceRecordRespVO {

    @Schema(description = "明细编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "员工编号", example = "1")
    private Long employeeId;

    @Schema(description = "员工姓名", example = "吴彬")
    private String employeeName;

    @Schema(description = "工号", example = "QC000007")
    private String jobNumber;

    @Schema(description = "年", example = "2026")
    private Integer year;

    @Schema(description = "月", example = "9")
    private Integer month;

    @Schema(description = "工程量/工序名称", example = "管道保温-岩棉")
    private String itemName;

    @Schema(description = "单位", example = "㎡")
    private String unit;

    @Schema(description = "完成工程量", example = "320")
    private BigDecimal quantity;

    @Schema(description = "计件单价", example = "45")
    private BigDecimal unitPrice;

    @Schema(description = "计件金额（工程量×单价）", example = "14400.00")
    private BigDecimal amount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
