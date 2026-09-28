package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - HRM 比例上限配置 修改 Request VO")
@Data
public class HrmSalaryRateLimitSaveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "比例上限（%），考勤计薪项使用", example = "100")
    @DecimalMin(value = "0.00", message = "比例上限不能小于 0")
    @Digits(integer = 4, fraction = 2, message = "比例上限最多 4 位整数和 2 位小数")
    private BigDecimal rateLimit;

    @Schema(description = "公司缴纳比例上限（%），参保项目使用", example = "25")
    @DecimalMin(value = "0.00", message = "公司缴纳比例上限不能小于 0")
    @Digits(integer = 4, fraction = 2, message = "公司缴纳比例上限最多 4 位整数和 2 位小数")
    private BigDecimal corporateRateLimit;

    @Schema(description = "个人缴纳比例上限（%），参保项目使用", example = "10")
    @DecimalMin(value = "0.00", message = "个人缴纳比例上限不能小于 0")
    @Digits(integer = 4, fraction = 2, message = "个人缴纳比例上限最多 4 位整数和 2 位小数")
    private BigDecimal personalRateLimit;

    @Schema(description = "状态：0 启用，1 停用（停用表示不做上限校验）", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
