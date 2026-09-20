package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.beforeOrEqualNow;

@Schema(description = "管理后台 - HRM 员工确认入职 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmEmployeeConfirmEntryReqVO extends HrmEmployeeSaveReqVO {

    @Schema(description = "应聘记录编号；招聘流程确认入职时使用", example = "1024")
    private Long applicationId;

    @AssertTrue(message = "入职时间不能晚于当前时间")
    @JsonIgnore
    public boolean isEntryTimeValid() {
        return getEntryTime() == null || beforeOrEqualNow(getEntryTime());
    }

}
