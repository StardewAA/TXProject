package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 员工离职交接进度 Response VO")
@Data
public class HrmEmployeeQuitHandoverProgressRespVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;

    @Schema(description = "是否存在离职单（无离职单时前端不展示交接卡片）", example = "true")
    private Boolean hasQuitInfo;

    @Schema(description = "员工姓名", example = "张三")
    private String employeeName;

    @Schema(description = "工号", example = "QC000008")
    private String jobNumber;

    @Schema(description = "计划离职时间")
    private LocalDateTime planQuitTime;

    @Schema(description = "交接状态：0未开始 1进行中 2已确认 3无需交接", example = "1")
    private Integer handoverStatus;

    @Schema(description = "交接状态名称", example = "进行中")
    private String handoverStatusName;

    @Schema(description = "交接人（员工编号）")
    private Long handoverToEmployeeId;

    @Schema(description = "交接人姓名")
    private String handoverToEmployeeName;

    @Schema(description = "交接确认时间")
    private LocalDateTime handoverConfirmTime;

    @Schema(description = "交接说明")
    private String handoverRemark;

    @Schema(description = "明细总数", example = "3")
    private Integer itemCount;

    @Schema(description = "待交回数量", example = "1")
    private Integer pendingItemCount;

    @Schema(description = "缺失未交回数量", example = "0")
    private Integer lostItemCount;

    @Schema(description = "交接是否可以确认（无待交回项即可确认）", example = "false")
    private Boolean confirmable;

    @Schema(description = "交接明细")
    private List<HrmEmployeeQuitHandoverItemRespVO> items;

    @Schema(description = "薪资结算时间")
    private LocalDateTime salarySettlementTime;

    @Schema(description = "计薪截止日（默认=离职生效日）")
    private java.time.LocalDate salaryCutoffDate;

    @Schema(description = "工资是否已结清", example = "false")
    private Boolean salarySettled;

    @Schema(description = "工资结清时间")
    private LocalDateTime salarySettleTime;

    @Schema(description = "结算说明")
    private String salarySettleRemark;

    @Schema(description = "离职闭环是否完成（交接有结论且工资已结清）", example = "false")
    private Boolean closed;

}
