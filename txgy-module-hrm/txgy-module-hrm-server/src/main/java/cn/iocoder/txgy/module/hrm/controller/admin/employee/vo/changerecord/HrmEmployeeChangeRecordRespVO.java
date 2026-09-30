package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 员工异动记录 Response VO")
@Data
public class HrmEmployeeChangeRecordRespVO {

    @Schema(description = "异动编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;

    @Schema(description = "变动类型", example = "5")
    private Integer type;

    @Schema(description = "异动原因", example = "1")
    private Integer reason;

    @Schema(description = "原部门编号", example = "100")
    private Long oldDeptId;

    @Schema(description = "原部门名称", example = "研发部")
    private String oldDeptName;

    @Schema(description = "新部门编号", example = "200")
    private Long newDeptId;

    @Schema(description = "新部门名称", example = "产品部")
    private String newDeptName;

    @Schema(description = "原岗位名称")
    private String oldPostName;

    @Schema(description = "新岗位名称")
    private String newPostName;

    @Schema(description = "原职级")
    private String oldPostLevel;

    @Schema(description = "新职级")
    private String newPostLevel;

    @Schema(description = "原工作地点")
    private String oldWorkAddress;

    @Schema(description = "新工作地点")
    private String newWorkAddress;

    @Schema(description = "原直属上级员工编号")
    private Long oldLeaderEmployeeId;

    @Schema(description = "原直属上级员工姓名")
    private String oldLeaderEmployeeName;

    @Schema(description = "新直属上级员工编号")
    private Long newLeaderEmployeeId;

    @Schema(description = "新直属上级员工姓名")
    private String newLeaderEmployeeName;

    @Schema(description = "试用期，单位月")
    private Integer probation;

    @Schema(description = "生效日期")
    private LocalDateTime effectTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "异动来源：1后台办理 2审批通过 3系统生效 4再入职 5取消离职", example = "1")
    private Integer source;

    @Schema(description = "异动来源名称", example = "后台办理")
    private String sourceName;

    @Schema(description = "经办人姓名", example = "赵敏")
    private String operatorName;

    @Schema(description = "审批单编号")
    private Long approvalId;

    @Schema(description = "审批人姓名", example = "李经理")
    private String approverName;

    @Schema(description = "审批通过时间")
    private LocalDateTime approveTime;

    @Schema(description = "撤销状态：0正常 1已撤销", example = "0")
    private Integer cancelStatus;

    @Schema(description = "是否已撤销", example = "false")
    private Boolean canceled;

    @Schema(description = "撤销原因")
    private String cancelReason;

    @Schema(description = "撤销时间")
    private LocalDateTime cancelTime;

    @Schema(description = "撤销人姓名")
    private String cancelUserName;

    @Schema(description = "是否已生效（可据此判断能否撤销）", example = "false")
    private Boolean applied;

    @Schema(description = "实际生效时间")
    private LocalDateTime appliedTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
