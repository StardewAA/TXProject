package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmHealthCheckResultEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - HRM 体检记录创建/修改 Request VO")
@Data
public class HrmHealthCheckSaveReqVO {

    @Schema(description = "编号（新增留空）", example = "1")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "124")
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Schema(description = "体检类型：1 入职，2 在岗，3 离职，4 职业健康", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "体检类型不能为空")
    private Integer checkType;

    @Schema(description = "体检日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "体检日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate checkDate;

    @Schema(description = "体检机构", example = "榆林市职业病防治院")
    @Size(max = 128, message = "体检机构长度不能超过 128 个字符")
    private String institution;

    @Schema(description = "体检结论：1 合格，2 不合格，3 需复查", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "体检结论不能为空")
    @InEnum(value = HrmHealthCheckResultEnum.class, message = "体检结论必须是 {value}")
    private Integer result;

    @Schema(description = "复查日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate recheckDate;

    @Schema(description = "有效期至（为空表示长期有效）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate validUntil;

    @Schema(description = "体检报告附件")
    private List<String> fileUrls;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
