package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件续证/复审记录 Response VO")
@Data
@Accessors(chain = true)
public class HrmCertificateRenewalRespVO {

    @Schema(description = "续证/复审记录编号", example = "1024")
    private Long id;

    @Schema(description = "证件台账编号", example = "2048")
    private Long certificateId;

    @Schema(description = "员工编号", example = "4096")
    private Long employeeId;

    @Schema(description = "续证/复审类型，参见 hrm_certificate_renewal_type", example = "1")
    private Integer type;

    @Schema(description = "办理前证件编号", example = "T44030019900718001X")
    private String oldNo;

    @Schema(description = "办理后证件编号", example = "T44030019900718001X-2029")
    private String newNo;

    @Schema(description = "办理前有效开始日期")
    private LocalDateTime oldStartTime;

    @Schema(description = "办理前有效结束日期")
    private LocalDateTime oldEndTime;

    @Schema(description = "办理后有效开始日期")
    private LocalDateTime newStartTime;

    @Schema(description = "办理后有效结束日期")
    private LocalDateTime newEndTime;

    @Schema(description = "复审日期")
    private LocalDateTime reviewTime;

    @Schema(description = "发证机构", example = "深圳市应急管理局")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDateTime issuingTime;

    @Schema(description = "新证件附件地址")
    private String attachmentUrl;

    @Schema(description = "经办人用户编号", example = "1")
    private Long operatorUserId;

    @Schema(description = "办理时间")
    private LocalDateTime renewTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
