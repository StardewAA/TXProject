package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmHealthCheckResultEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 体检记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmHealthCheckPageReqVO extends PageParam {

    @Schema(description = "员工编号", example = "124")
    private Long employeeId;

    @Schema(description = "体检类型：1 入职，2 在岗，3 离职，4 职业健康", example = "1")
    private Integer checkType;

    @Schema(description = "体检结论：1 合格，2 不合格，3 需复查", example = "1")
    @InEnum(value = HrmHealthCheckResultEnum.class, message = "体检结论必须是 {value}")
    private Integer result;

}
