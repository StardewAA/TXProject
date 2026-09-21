package cn.iocoder.txgy.module.hrm.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmSafetyEducationLevelEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 三级安全教育分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmSafetyEducationPageReqVO extends PageParam {

    @Schema(description = "员工编号", example = "124")
    private Long employeeId;

    @Schema(description = "教育级别：1 公司级，2 项目级，3 班组级", example = "2")
    @InEnum(value = HrmSafetyEducationLevelEnum.class, message = "教育级别必须是 {value}")
    private Integer level;

    @Schema(description = "是否合格", example = "true")
    private Boolean qualified;

}
