package cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - HRM 请假「代提 / 补录」Request VO（Q-004 / I-006）
 *
 * <p>由 HR 代员工补录请假：继承员工端创建请假单的字段，额外要求指定员工；
 * {@code approveImmediately=true} 时单据直接落「已通过」（HR 补录历史假），否则进入「审批中」由 HR 走正常审批。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 请假代提/补录 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmAttendanceLeaveAdminCreateReqVO extends HrmAttendanceLeaveCreateReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Schema(description = "是否直接生效（HR 补录历史假）：true=已通过，false=进入审批中", example = "true")
    private Boolean approveImmediately;

}
