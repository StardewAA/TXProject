package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 证件预警分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmCertificateWarningPageReqVO extends PageParam {

    @Schema(description = "证件台账编号", example = "2048")
    private Long certificateId;

    @Schema(description = "员工编号", example = "4096")
    private Long employeeId;

    @Schema(description = "预警类型，参见 hrm_certificate_warning_type", example = "1")
    private Integer type;

    @Schema(description = "处理状态，参见 hrm_certificate_warning_status", example = "0")
    private Integer status;

    @Schema(description = "通知状态", example = "0")
    private Integer notifyStatus;

    @Schema(description = "预警日期范围开始", example = "2026-09-01")
    private LocalDate warningDateStart;

    @Schema(description = "预警日期范围结束", example = "2026-09-30")
    private LocalDate warningDateEnd;

}
