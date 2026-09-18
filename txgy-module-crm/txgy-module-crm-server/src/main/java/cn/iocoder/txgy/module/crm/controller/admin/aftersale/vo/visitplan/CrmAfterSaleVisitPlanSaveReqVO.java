package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSalePlanTypeEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitCycleEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitPlanStatusEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 售后回访计划 新增/修改 Request VO")
@Data
public class CrmAfterSaleVisitPlanSaveReqVO {

    @Schema(description = "编号，主键自增（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "计划名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "质保期内季度回访")
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 128, message = "计划名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "关联质保档案编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "质保档案编号不能为空")
    private Long warrantyId;

    @Schema(description = "计划类型：1=手动 2=自动", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "计划类型不能为空")
    @InEnum(CrmAfterSalePlanTypeEnum.class)
    private Integer planType;

    @Schema(description = "周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "周期类型不能为空")
    @InEnum(CrmAfterSaleVisitCycleEnum.class)
    private Integer cycleType;

    @Schema(description = "周期倍数 或 到期前天数（cycleType=4 时）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "周期倍数/到期前天数不能为空")
    @Min(value = 1, message = "周期倍数/到期前天数最小为 1")
    @Max(value = 12, message = "周期倍数/到期前天数最大为 12")
    private Integer cycleValue;

    @Schema(description = "计划生效开始日期；为空默认质保开始日期（手动计划默认今天）", example = "2026-09-07")
    private LocalDate startDate;

    @Schema(description = "计划生效结束日期；为空默认质保到期日", example = "2028-09-06")
    private LocalDate endDate;

    @Schema(description = "任务处理人类型：1=售后人员 2=业务员；为空默认 1", example = "1")
    private Integer assigneeType;

    @Schema(description = "指定处理人（为空则按任务处理人类型取质保档案的售后责任人/业务负责人）", example = "1")
    private Long assigneeUserId;

    @Schema(description = "默认回访方式：1=电话 2=上门 3=微信 4=邮件；为空默认电话", example = "1")
    @InEnum(CrmAfterSaleVisitTypeEnum.class)
    private Integer visitType;

    @Schema(description = "计划状态：1=启用 2=停用 3=已完成；为空默认启用", example = "1")
    @InEnum(CrmAfterSaleVisitPlanStatusEnum.class)
    private Integer status;

    @Schema(description = "备注", example = "重点关注涂层脱落风险")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
