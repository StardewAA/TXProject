package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 用工需求单 Response VO")
@Data
public class HrmRecruitDemandRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "需求单号", example = "QC-2026-0001")
    private String demandNo;

    @Schema(description = "需求名称", example = "保温工用人需求")
    private String demandName;

    @Schema(description = "用工部门编号", example = "100")
    private Long deptId;

    @Schema(description = "用工部门名称", example = "秦川项目部")
    private String deptName;

    @Schema(description = "用工主体编号", example = "1")
    private Long companyProfileId;

    @Schema(description = "用工主体名称", example = "秦川化工产业园发展有限公司")
    private String companyProfileName;

    @Schema(description = "工种", example = "保温工")
    private String workType;

    @Schema(description = "证照要求", example = "高处作业证、焊工证")
    private String certRequire;

    @Schema(description = "需求人数", example = "20")
    private Integer headcount;

    @Schema(description = "已到岗人数", example = "6")
    private Integer onboardCount;

    @Schema(description = "缺口人数", example = "14")
    private Integer gapCount;

    @Schema(description = "计划进场日期", example = "2026-10-08")
    private LocalDate planEntryDate;

    @Schema(description = "参考单价", example = "320 元/天")
    private String expectSalary;

    @Schema(description = "紧急程度", example = "2")
    private Integer urgency;

    @Schema(description = "紧急程度名称", example = "紧急")
    private String urgencyName;

    @Schema(description = "状态", example = "1")
    private Integer status;

    @Schema(description = "状态名称", example = "待招聘")
    private String statusName;

    @Schema(description = "提出人", example = "王工")
    private String applicant;

    @Schema(description = "联系方式", example = "13800000000")
    private String contactMobile;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
