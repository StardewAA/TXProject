package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 用工需求单 新增/修改 Request VO")
@Data
public class HrmRecruitDemandSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "需求名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "秦川化工园区二期 保温工用人需求")
    @NotEmpty(message = "需求名称不能为空")
    @Size(max = 128, message = "需求名称不能超过 128 个字符")
    private String demandName;

    @Schema(description = "用工部门 / 项目部编号", example = "100")
    private Long deptId;

    @Schema(description = "用工主体编号", example = "1")
    private Long companyProfileId;

    @Schema(description = "工种", example = "保温工")
    @Size(max = 64, message = "工种不能超过 64 个字符")
    private String workType;

    @Schema(description = "证照要求（多个用、分隔）", example = "高处作业证、焊工证")
    @Size(max = 512, message = "证照要求不能超过 512 个字符")
    private String certRequire;

    @Schema(description = "需求人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "需求人数不能为空")
    @Min(value = 1, message = "需求人数必须大于 0")
    private Integer headcount;

    @Schema(description = "计划进场日期", example = "2026-10-08")
    private LocalDate planEntryDate;

    @Schema(description = "参考单价", example = "320 元/天 或 45 元/㎡")
    @Size(max = 64, message = "参考单价不能超过 64 个字符")
    private String expectSalary;

    @Schema(description = "紧急程度：1 普通，2 紧急，3 特急", example = "2")
    private Integer urgency;

    @Schema(description = "状态：1 待招聘，2 招聘中，3 已完成，4 已取消", example = "1")
    private Integer status;

    @Schema(description = "提出人", example = "王工")
    @Size(max = 64, message = "提出人不能超过 64 个字符")
    private String applicant;

    @Schema(description = "联系方式", example = "13800000000")
    @Size(max = 32, message = "联系方式不能超过 32 个字符")
    private String contactMobile;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
