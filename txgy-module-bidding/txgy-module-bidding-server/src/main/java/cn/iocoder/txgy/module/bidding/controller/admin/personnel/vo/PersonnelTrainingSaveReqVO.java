package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工培训记录登记入参。
 *
 * <p>规范出处：用户业务口径"记录员工基本信息、资质证书、培训记录等"；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（edu_hours 继续教育学时=达标要素）。
 * <p>业务目标：培训与继续教育学时可累计、可追溯，支撑资质人员指标核验。
 */
@Schema(description = "管理后台 - 员工培训记录登记入参")
@Data
public class PersonnelTrainingSaveReqVO {

    @Schema(description = "归属企业", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "归属企业不能为空")
    private Long entId;

    @Schema(description = "参训员工", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "参训员工不能为空")
    private Long personId;

    @Schema(description = "培训名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026 年度安全生产继续教育")
    @NotBlank(message = "培训名称不能为空")
    private String trainingName;

    @Schema(description = "培训类型 code：SAFETY/CONTINUE_EDU/SKILL/PRE_JOB/OTHER", example = "CONTINUE_EDU")
    private String trainingType;

    @Schema(description = "培训机构/组织方")
    private String organizer;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "学时（继续教育达标要素）", example = "32.0")
    private BigDecimal eduHours;

    @Schema(description = "结果 code：QUALIFIED/UNQUALIFIED/ATTENDING", example = "QUALIFIED")
    private String result;

    @Schema(description = "考核成绩", example = "88.50")
    private BigDecimal score;

    @Schema(description = "培训合格证编号")
    private String certNo;

    @Schema(description = "培训合格证有效期止")
    private LocalDate certValidTo;

    @Schema(description = "培训证明文件 id")
    private Long fileId;

    @Schema(description = "备注")
    private String remark;
}
