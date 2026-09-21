package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - HRM 用工需求单 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmRecruitDemandPageReqVO extends PageParam {

    @Schema(description = "需求单号", example = "QC-2026-0001")
    private String demandNo;

    @Schema(description = "需求名称", example = "保温工用人需求")
    private String demandName;

    @Schema(description = "工种", example = "保温工")
    private String workType;

    @Schema(description = "用工部门 / 项目部编号", example = "100")
    private Long deptId;

    @Schema(description = "状态：1 待招聘，2 招聘中，3 已完成，4 已取消", example = "1")
    private Integer status;

    @Schema(description = "紧急程度：1 普通，2 紧急，3 特急", example = "2")
    private Integer urgency;

    @Schema(description = "计划进场日期区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] planEntryDate;

}
