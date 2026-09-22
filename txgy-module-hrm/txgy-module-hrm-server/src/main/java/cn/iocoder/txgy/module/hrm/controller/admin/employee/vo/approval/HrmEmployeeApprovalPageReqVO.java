package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 员工异动审批 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmEmployeeApprovalPageReqVO extends PageParam {

    @Schema(description = "员工编号", example = "131")
    private Long employeeId;

    @Schema(description = "审批类型：1 转正 2 调岗/晋升 3 离职", example = "1")
    private Integer type;

    @Schema(description = "状态：1 待审批 2 已通过 3 已驳回", example = "1")
    private Integer status;

}
