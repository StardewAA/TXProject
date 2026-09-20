package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 部门编制 Response VO")
@Data
public class HrmDeptHeadcountRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;
    @Schema(description = "部门编号", example = "100")
    private Long deptId;
    @Schema(description = "部门名称", example = "秦川项目部")
    private String deptName;
    @Schema(description = "编制人数", example = "30")
    private Integer headcount;
    @Schema(description = "在册人数", example = "17")
    private Long onboardCount;
    @Schema(description = "缺口（编制 - 在册，负数表示超编）", example = "13")
    private Long gapCount;
    @Schema(description = "是否超编", example = "false")
    private Boolean overStaffed;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
