package cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.isAfterOrEqual;

/**
 * 员工端「我的档案」- 工作经历保存 Request VO
 *
 * <p>与后台 VO 的差异：不接收 employeeId（服务端强制为本人）；「职务」放宽为选填，
 * 因为员工自助补录时未必记得历史职务的准确名称。</p>
 */
@Schema(description = "员工端 - HRM 我的工作经历保存 Request VO")
@Data
public class HrmPortalEmployeeWorkExperienceSaveReqVO {

    @Schema(description = "工作经历编号（新增时为空）", example = "1024")
    private Long id;

    @Schema(description = "工作单位", requiredMode = Schema.RequiredMode.REQUIRED, example = "示例科技")
    @NotBlank(message = "工作单位不能为空")
    @Size(max = 255, message = "工作单位长度不能超过 255 个字符")
    private String workUnit;

    @Schema(description = "职务", example = "开发工程师")
    @Size(max = 255, message = "职务长度不能超过 255 个字符")
    private String postName;

    @Schema(description = "工作开始日期")
    private LocalDateTime startTime;

    @Schema(description = "工作结束日期")
    private LocalDateTime endTime;

    @Schema(description = "离职原因", example = "职业发展")
    @Size(max = 1024, message = "离职原因长度不能超过 1024 个字符")
    private String reason;

    @Schema(description = "证明人", example = "李四")
    @Size(max = 255, message = "证明人长度不能超过 255 个字符")
    private String witnessName;

    @Schema(description = "证明人手机号", example = "15601691301")
    @Size(max = 32, message = "证明人手机号长度不能超过 32 个字符")
    private String witnessPhone;

    @Schema(description = "工作备注", example = "负责核心业务研发")
    @Size(max = 500, message = "工作备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @AssertTrue(message = "工作经历的结束日期不能早于开始日期")
    @JsonIgnore
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || isAfterOrEqual(endTime, startTime);
    }

}
