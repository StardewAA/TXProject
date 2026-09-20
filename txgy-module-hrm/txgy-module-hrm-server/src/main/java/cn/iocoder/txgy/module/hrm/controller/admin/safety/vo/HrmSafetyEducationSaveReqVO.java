package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmSafetyEducationLevelEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - HRM 三级安全教育创建/修改 Request VO")
@Data
public class HrmSafetyEducationSaveReqVO {

    @Schema(description = "编号（新增留空）", example = "1")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "124")
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Schema(description = "教育级别：1 公司级，2 项目级，3 班组级", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "教育级别不能为空")
    @InEnum(value = HrmSafetyEducationLevelEnum.class, message = "教育级别必须是 {value}")
    private Integer level;

    @Schema(description = "教育日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "教育日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate educationDate;

    @Schema(description = "教育人", example = "吴斌")
    @Size(max = 64, message = "教育人长度不能超过 64 个字符")
    private String educatorName;

    @Schema(description = "学时", example = "8")
    private BigDecimal hours;

    @Schema(description = "教育内容", example = "高处作业风险与防护、动火作业规程")
    @Size(max = 1000, message = "教育内容长度不能超过 1000 个字符")
    private String content;

    @Schema(description = "考核成绩", example = "92")
    private BigDecimal examScore;

    @Schema(description = "是否合格", example = "true")
    private Boolean qualified;

    @Schema(description = "签字表/试卷/照片附件")
    private List<String> fileUrls;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
