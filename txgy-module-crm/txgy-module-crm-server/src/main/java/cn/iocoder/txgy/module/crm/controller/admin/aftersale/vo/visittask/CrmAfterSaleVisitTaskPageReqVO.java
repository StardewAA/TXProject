package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 售后回访任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleVisitTaskPageReqVO extends PageParam {

    /**
     * 提醒类型 - 临期（待办「回访任务临期」使用）
     *
     * <p>实时查询口径：任务状态 = 待处理，且计划回访日期 ≤ 今天 + 售后设置「任务临期提醒提前天数」
     */
    public static final Integer REMIND_TYPE_APPROACHING = 1;

    @Schema(description = "来源计划编号", example = "1")
    private Long planId;

    @Schema(description = "质保档案编号", example = "1")
    private Long warrantyId;

    @Schema(description = "客户编号", example = "12")
    private Long customerId;

    @Schema(description = "处理人用户编号", example = "1")
    private Long assigneeUserId;

    @Schema(description = "任务状态：1=待处理 2=已完成 3=已逾期", example = "1")
    private Integer status;

    @Schema(description = "计划执行日期区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] taskDate;

    @Schema(description = "提醒类型：1=临期（待处理，且计划回访日在提前提醒天数内）；为空表示全部", example = "1")
    private Integer remindType;

}
