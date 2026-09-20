package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 绩效结果取数 Response VO
 *
 * <p>月度工资表生成时按绩效结果取"绩效系数/等级/得分"，用于算绩效工资
 * （绩效工资 = 绩效工资基数 × 绩效系数）。数据来源：已完成归档的个人考核单。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 绩效结果取数 Response VO")
@Data
public class HrmSalaryPerformanceRespVO {

    @Schema(description = "考核单编号", example = "1")
    private Long assessmentId;
    @Schema(description = "考核计划编号", example = "1")
    private Long planId;
    @Schema(description = "考核计划名称", example = "2026年8月 项目部月度KPI考核")
    private String planName;
    @Schema(description = "员工编号", example = "131")
    private Long employeeId;
    @Schema(description = "员工姓名", example = "李保温")
    private String employeeName;
    @Schema(description = "工号", example = "QC000011")
    private String jobNumber;
    @Schema(description = "考核得分", example = "88.50")
    private BigDecimal score;
    @Schema(description = "结果等级", example = "B")
    private String resultLevel;
    @Schema(description = "绩效系数", example = "1.10")
    private BigDecimal coefficient;
    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;
    @Schema(description = "是否找到已完成的绩效结果", example = "true")
    private Boolean found;

}
