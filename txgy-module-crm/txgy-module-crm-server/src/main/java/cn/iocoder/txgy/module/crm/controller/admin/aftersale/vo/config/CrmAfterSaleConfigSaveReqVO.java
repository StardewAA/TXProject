package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 售后设置 更新 Request VO（单行配置）")
@Data
public class CrmAfterSaleConfigSaveReqVO {

    @Schema(description = "质保到期提前提醒天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    @NotNull(message = "质保到期提前提醒天数不能为空")
    @Min(value = 7, message = "质保到期提前提醒天数范围 7-180")
    @Max(value = 180, message = "质保到期提前提醒天数范围 7-180")
    private Integer warrantyWarnDays;

    @Schema(description = "是否开启自动生成回访计划", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否开启自动回访不能为空")
    private Boolean autoVisitEnabled;

    @Schema(description = "自动回访周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访", example = "1")
    private Integer autoVisitCycleType;

    @Schema(description = "自动回访周期倍数/到期前天数", example = "0")
    @Min(value = 0, message = "自动回访周期配置不正确")
    private Integer autoVisitCycleValue;

    @Schema(description = "回访任务临期提醒提前天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "回访任务临期提醒提前天数不能为空")
    @Min(value = 0, message = "回访任务临期提醒提前天数不能小于 0")
    private Integer taskRemindDays;

    @Schema(description = "回访任务逾期判定天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "回访任务逾期判定天数不能为空")
    @Min(value = 1, message = "回访任务逾期判定天数不能小于 1")
    private Integer taskOverdueDays;

    @Schema(description = "工单超时未处理预警天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    @NotNull(message = "工单超时未处理预警天数不能为空")
    @Min(value = 1, message = "工单超时未处理预警天数不能小于 1")
    private Integer ticketOverdueDays;

}
