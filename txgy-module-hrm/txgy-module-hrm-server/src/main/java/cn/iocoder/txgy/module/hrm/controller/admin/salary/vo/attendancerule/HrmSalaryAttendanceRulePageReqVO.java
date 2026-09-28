package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 考勤计薪规则 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmSalaryAttendanceRulePageReqVO extends PageParam {

    @Schema(description = "规则名称", example = "标准计薪规则")
    private String name;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

}
