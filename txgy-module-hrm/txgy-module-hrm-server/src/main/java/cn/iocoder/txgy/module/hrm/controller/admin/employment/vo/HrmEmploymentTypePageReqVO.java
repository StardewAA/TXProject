package cn.iocoder.txgy.module.hrm.controller.admin.employment.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.employment.HrmSalaryModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 用工形式分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmEmploymentTypePageReqVO extends PageParam {

    @Schema(description = "用工形式名称（模糊匹配）", example = "临时")
    private String name;

    @Schema(description = "用工形式编码", example = "TEMPORARY")
    private String code;

    @Schema(description = "默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件", example = "2")
    @InEnum(value = HrmSalaryModeEnum.class, message = "默认计薪方式必须是 {value}")
    private Integer salaryMode;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

}
