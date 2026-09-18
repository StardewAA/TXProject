package cn.iocoder.txgy.module.crm.controller.admin.business.vo.business;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - CRM 商机赢单 Request VO
 *
 * <p>点击「赢单」按钮后弹出，签约日期与签约金额必填；
 * 提交后商机状态组=已赢单、预估赢单率=100，并锁定关键字段。
 *
 * @author 防腐保温项目组
 */
@Schema(description = "管理后台 - CRM 商机赢单 Request VO")
@Data
public class CrmBusinessWinReqVO {

    @Schema(description = "商机编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "32129")
    @NotNull(message = "商机编号不能为空")
    private Long id;

    @Schema(description = "签约日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "签约日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime signDate;

    @Schema(description = "签约金额（元）", requiredMode = Schema.RequiredMode.REQUIRED, example = "120000.00")
    @NotNull(message = "签约金额不能为空")
    @DecimalMin(value = "0", message = "签约金额不能小于 0")
    private BigDecimal signAmount;

    @Schema(description = "赢单备注", example = "客户已完成合同签订")
    @Size(max = 500, message = "赢单备注长度不能超过 500 个字符")
    private String remark;

}
