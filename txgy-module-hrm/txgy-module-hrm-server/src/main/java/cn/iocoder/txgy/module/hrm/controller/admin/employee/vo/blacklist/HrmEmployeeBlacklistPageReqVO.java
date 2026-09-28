package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 员工黑名单 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmEmployeeBlacklistPageReqVO extends PageParam {

    @Schema(description = "姓名", example = "李四")
    private String name;

    @Schema(description = "手机号", example = "13800000000")
    private String mobile;

    @Schema(description = "身份证号", example = "610100199001011234")
    private String idCardNo;

    @Schema(description = "员工编号", example = "1024")
    private Long employeeId;

    @Schema(description = "状态：1 生效，2 已解除", example = "1")
    private Integer status;

    @Schema(description = "来源", example = "1")
    private Integer source;

}
