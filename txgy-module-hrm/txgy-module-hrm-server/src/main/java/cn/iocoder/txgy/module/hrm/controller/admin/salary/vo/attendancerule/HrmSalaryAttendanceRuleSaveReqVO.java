package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 考勤计薪规则 新增/修改 Request VO")
@Data
public class HrmSalaryAttendanceRuleSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐保温工程队标准计薪规则")
    @NotEmpty(message = "规则名称不能为空")
    private String name;

    @Schema(description = "满勤天数（当月应出勤天数）", requiredMode = Schema.RequiredMode.REQUIRED, example = "26")
    @NotNull(message = "满勤天数不能为空")
    private BigDecimal fullAttendanceDays;

    @Schema(description = "日薪折算方式：1 月薪÷满勤天数，2 按日薪直接取", example = "1")
    private Integer dailyWageMode;

    @Schema(description = "缺勤扣款比例（%）", example = "100")
    private BigDecimal absenceDeductRate;

    @Schema(description = "满勤奖比例（%，按日薪）", example = "10")
    private BigDecimal fullAttendanceBonusRate;

    @Schema(description = "事假扣款比例（%）", example = "100")
    private BigDecimal personalLeaveRate;

    @Schema(description = "病假扣款比例（%）", example = "60")
    private BigDecimal sickLeaveRate;

    @Schema(description = "迟到早退几次折算 1 天缺勤（0 表示不折算）", example = "3")
    private Integer lateToAbsenceTimes;

    @Schema(description = "是否默认规则", example = "true")
    private Boolean defaultFlag;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
