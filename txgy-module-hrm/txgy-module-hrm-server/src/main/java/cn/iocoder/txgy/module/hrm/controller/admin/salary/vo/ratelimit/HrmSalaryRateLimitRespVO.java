package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - HRM 比例上限配置 Response VO")
@Data
public class HrmSalaryRateLimitRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "业务类型：1 考勤计薪规则 2 参保方案", example = "1")
    private Integer bizType;

    @Schema(description = "计薪项/参保项编码", example = "absenceDeductRate")
    private String itemCode;

    @Schema(description = "计薪项/参保项名称", example = "缺勤扣款比例")
    private String itemName;

    @Schema(description = "比例上限（%）", example = "100")
    private BigDecimal rateLimit;

    @Schema(description = "公司缴纳比例上限（%）", example = "25")
    private BigDecimal corporateRateLimit;

    @Schema(description = "个人缴纳比例上限（%）", example = "10")
    private BigDecimal personalRateLimit;

    @Schema(description = "状态：0 启用 1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
