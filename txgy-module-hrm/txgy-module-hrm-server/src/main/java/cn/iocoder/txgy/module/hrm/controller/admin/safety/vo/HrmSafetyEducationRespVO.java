package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 三级安全教育 Response VO")
@Data
public class HrmSafetyEducationRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "员工编号", example = "124")
    private Long employeeId;

    @Schema(description = "姓名", example = "吴斌")
    private String employeeName;

    @Schema(description = "工号", example = "QC000006")
    private String jobNumber;

    @Schema(description = "教育级别", example = "2")
    private Integer level;

    @Schema(description = "教育级别名称", example = "项目级")
    private String levelName;

    @Schema(description = "教育日期")
    private LocalDate educationDate;

    @Schema(description = "教育人")
    private String educatorName;

    @Schema(description = "学时")
    private BigDecimal hours;

    @Schema(description = "教育内容")
    private String content;

    @Schema(description = "考核成绩")
    private BigDecimal examScore;

    @Schema(description = "是否合格")
    private Boolean qualified;

    @Schema(description = "附件")
    private List<String> fileUrls;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
