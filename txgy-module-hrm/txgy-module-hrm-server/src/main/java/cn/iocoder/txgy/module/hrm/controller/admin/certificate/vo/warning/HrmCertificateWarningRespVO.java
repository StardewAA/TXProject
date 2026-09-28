package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件预警 Response VO")
@Data
@Accessors(chain = true)
public class HrmCertificateWarningRespVO {

    @Schema(description = "预警记录编号", example = "1024")
    private Long id;

    @Schema(description = "证件台账编号", example = "2048")
    private Long certificateId;

    @Schema(description = "证件名称", example = "低压电工作业操作证")
    private String certificateName;

    @Schema(description = "证件编号", example = "T44030019900718001X")
    private String certificateNo;

    @Schema(description = "证件类型名称", example = "特种作业操作证")
    private String certificateTypeName;

    @Schema(description = "员工编号", example = "4096")
    private Long employeeId;

    @Schema(description = "员工姓名", example = "张三")
    private String employeeName;

    @Schema(description = "员工工号", example = "ADMIN-001")
    private String employeeJobNumber;

    @Schema(description = "部门编号", example = "103")
    private Long deptId;

    @Schema(description = "预警类型，参见 hrm_certificate_warning_type", example = "1")
    private Integer type;

    @Schema(description = "预警触发日期", example = "2026-09-18")
    private LocalDate warningDate;

    @Schema(description = "到期日期")
    private LocalDateTime expireDate;

    @Schema(description = "剩余天数，负数表示已过期", example = "30")
    private Integer remainDays;

    @Schema(description = "处理状态，参见 hrm_certificate_warning_status", example = "0")
    private Integer status;

    @Schema(description = "处理人用户编号", example = "1")
    private Long handleUserId;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "处理意见", example = "已通知员工办理续证")
    private String handleRemark;

    @Schema(description = "通知状态", example = "0")
    private Integer notifyStatus;

    @Schema(description = "通知时间")
    private LocalDateTime notifyTime;

    @Schema(description = "通知失败原因")
    private String notifyFailReason;

    @Schema(description = "通知重试次数", example = "0")
    private Integer notifyRetryCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
