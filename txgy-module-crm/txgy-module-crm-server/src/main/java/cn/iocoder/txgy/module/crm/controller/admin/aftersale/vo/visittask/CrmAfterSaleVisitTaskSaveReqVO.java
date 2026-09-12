package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 售后回访任务 新增/修改 Request VO")
@Data
public class CrmAfterSaleVisitTaskSaveReqVO {

    @Schema(description = "编号，主键自增（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "来源计划编号；手动创建任务时为空（0）", example = "0")
    private Long planId;

    @Schema(description = "质保档案编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "质保档案编号不能为空")
    private Long warrantyId;

    @Schema(description = "任务处理人（售后人员或业务员）；为空默认质保售后责任人", example = "1")
    private Long assigneeUserId;

    @Schema(description = "计划执行日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-07")
    @NotNull(message = "计划执行日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate taskDate;

    @Schema(description = "任务备注/回访提示", example = "重点确认涂层状态")
    @NotBlank(message = "任务备注不能为空")
    @Size(max = 500, message = "任务备注长度不能超过 500 个字符")
    private String remark;

}
