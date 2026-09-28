package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quitinfo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 员工离职信息 Response VO")
@Data
public class HrmEmployeeQuitInfoRespVO {

    @Schema(description = "离职信息编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;

    @Schema(description = "计划离职时间")
    private LocalDateTime planQuitTime;

    @Schema(description = "申请离职时间")
    private LocalDateTime applyQuitTime;

    @Schema(description = "薪资结算时间")
    private LocalDateTime salarySettlementTime;

    @Schema(description = "离职类型", example = "1")
    private Integer type;

    @Schema(description = "离职原因", example = "1")
    private Integer reason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "离职前员工状态")
    private Integer oldEmployeeStatus;

    @Schema(description = "交接状态：0未开始 1进行中 2已确认 3无需交接", example = "2")
    private Integer handoverStatus;

    @Schema(description = "交接状态名称", example = "已确认")
    private String handoverStatusName;

    @Schema(description = "交接人（员工编号）")
    private Long handoverToEmployeeId;

    @Schema(description = "交接人姓名")
    private String handoverToEmployeeName;

    @Schema(description = "交接确认时间")
    private LocalDateTime handoverConfirmTime;

    @Schema(description = "交接说明")
    private String handoverRemark;

    @Schema(description = "计薪截止日（默认=离职生效日）")
    private LocalDate salaryCutoffDate;

    @Schema(description = "工资是否已结清", example = "true")
    private Boolean salarySettled;

    @Schema(description = "工资结清时间")
    private LocalDateTime salarySettleTime;

    @Schema(description = "结算说明")
    private String salarySettleRemark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
