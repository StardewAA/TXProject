package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 员工异动审批发起 Request VO")
@Data
public class HrmEmployeeApprovalStartReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "131")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "审批类型：1 转正，2 调岗/晋升，3 离职", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "审批类型不能为空")
    private Integer type;

    @Schema(description = "异动原因/申请说明", example = "试用期考核合格，申请转正")
    private String reason;

    @Schema(description = "期望生效时间", example = "2026-10-01 00:00:00")
    private LocalDateTime effectTime;

    @Schema(description = "备注")
    private String remark;

    // ========== 调岗/晋升（type=2）的目标任职信息：审批通过后据此真正落地 ==========

    @Schema(description = "目标部门编号（调岗/晋升）", example = "100")
    private Long targetDeptId;

    @Schema(description = "目标岗位名称（调岗/晋升）", example = "保温班组长")
    @Size(max = 128, message = "目标岗位名称不能超过 128 个字符")
    private String targetPostName;

    @Schema(description = "目标岗位职级（调岗/晋升）", example = "P3")
    @Size(max = 64, message = "目标岗位职级不能超过 64 个字符")
    private String targetPostLevel;

    @Schema(description = "目标直属上级员工编号（调岗/晋升）", example = "131")
    private Long targetLeaderEmployeeId;

    @Schema(description = "目标工作地址（调岗/晋升）", example = "陕西省西安市高陵区泾渭工业园")
    @Size(max = 255, message = "目标工作地址不能超过 255 个字符")
    private String targetWorkAddress;

    // ========== 离职（type=3）所需信息 ==========

    @Schema(description = "离职类型：1 主动离职，2 被动离职，3 退休（离职必填）", example = "1")
    private Integer quitType;

    @Schema(description = "离职原因（对齐 HrmEmployeeQuitReasonEnum，退休可不填）", example = "3")
    private Integer quitReason;

    @Schema(description = "薪资结算时间（离职必填）")
    private LocalDateTime salarySettlementTime;

    @Schema(description = "目标基本工资（晋升/调岗填写后，审批通过自动套薪）", example = "10000")
    @DecimalMin(value = "0", message = "目标基本工资不能小于 0")
    private java.math.BigDecimal targetBasicSalary;

    @Schema(description = "目标绩效工资", example = "3000")
    @DecimalMin(value = "0", message = "目标绩效工资不能小于 0")
    private java.math.BigDecimal targetPerformanceSalary;

}
