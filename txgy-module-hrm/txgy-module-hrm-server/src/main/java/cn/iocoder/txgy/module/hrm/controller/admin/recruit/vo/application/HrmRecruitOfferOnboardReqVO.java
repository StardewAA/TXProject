package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application;

import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@Schema(description = "管理后台 - HRM Offer 确认入职请求")
public class HrmRecruitOfferOnboardReqVO {
    @NotNull(message = "Offer 编号不能为空") private Long offerId;
    @NotNull(message = "聘用形式不能为空") private Integer type;
    @NotNull(message = "入职时间不能为空") @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime entryTime;
    private Integer entryStatus = HrmEmployeeEntryStatusEnum.ACTIVE.getStatus();
    private Integer status;
}
