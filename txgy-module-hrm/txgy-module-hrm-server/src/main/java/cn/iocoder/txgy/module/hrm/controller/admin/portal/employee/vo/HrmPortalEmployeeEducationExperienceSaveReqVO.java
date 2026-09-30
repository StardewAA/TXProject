package cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.isAfterOrEqual;

/**
 * 员工端「我的档案」- 教育经历保存 Request VO
 *
 * <p>与后台 VO 的差异：不接收 employeeId（由服务端强制为登录人本人的员工档案），
 * 避免员工越权写入他人档案。</p>
 */
@Schema(description = "员工端 - HRM 我的教育经历保存 Request VO")
@Data
public class HrmPortalEmployeeEducationExperienceSaveReqVO {

    @Schema(description = "教育经历编号（新增时为空）", example = "1024")
    private Long id;

    @Schema(description = "学历", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    @NotNull(message = "学历不能为空")
    private Integer education;

    @Schema(description = "毕业院校", requiredMode = Schema.RequiredMode.REQUIRED, example = "浙江大学")
    @NotBlank(message = "毕业院校不能为空")
    @Size(max = 255, message = "毕业院校长度不能超过 255 个字符")
    private String graduateSchool;

    @Schema(description = "专业", requiredMode = Schema.RequiredMode.REQUIRED, example = "软件工程")
    @NotBlank(message = "专业不能为空")
    @Size(max = 255, message = "专业长度不能超过 255 个字符")
    private String major;

    @Schema(description = "入学日期")
    private LocalDateTime admissionTime;

    @Schema(description = "毕业日期")
    private LocalDateTime graduationTime;

    @Schema(description = "教学方式", example = "1")
    private Integer teachingMethods;

    @Schema(description = "是否第一学历", example = "true")
    private Boolean firstDegree;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @AssertTrue(message = "毕业日期不能早于入学日期")
    @JsonIgnore
    public boolean isTimeRangeValid() {
        return admissionTime == null || graduationTime == null
                || isAfterOrEqual(graduationTime, admissionTime);
    }

}
