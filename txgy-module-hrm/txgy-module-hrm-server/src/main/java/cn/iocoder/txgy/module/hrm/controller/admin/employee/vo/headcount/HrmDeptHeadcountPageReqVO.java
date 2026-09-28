package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - HRM 部门编制 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HrmDeptHeadcountPageReqVO extends PageParam {

    @Schema(description = "部门编号", example = "100")
    private Long deptId;

}
