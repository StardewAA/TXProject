package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - HRM 员工部门统计 Response VO")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HrmEmployeeDeptStatisticsRespVO {

    /**
     * 便捷构造：只填「直属」人数，含下级人数由后端聚合后回填
     */
    public HrmEmployeeDeptStatisticsRespVO(Long deptId, Long activeCount,
                                           Long fullTimeCount, Long nonFullTimeCount) {
        this.deptId = deptId;
        this.activeCount = activeCount;
        this.fullTimeCount = fullTimeCount;
        this.nonFullTimeCount = nonFullTimeCount;
    }

    @Schema(description = "部门编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long deptId;

    @Schema(description = "在职员工人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long activeCount;

    @Schema(description = "全职员工人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Long fullTimeCount;

    @Schema(description = "非全职员工人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long nonFullTimeCount;

    @Schema(description = "在职员工人数（含下级部门）", example = "30")
    private Long totalActiveCount;

    @Schema(description = "全职员工人数（含下级部门）", example = "24")
    private Long totalFullTimeCount;

    @Schema(description = "非全职员工人数（含下级部门）", example = "6")
    private Long totalNonFullTimeCount;

}
