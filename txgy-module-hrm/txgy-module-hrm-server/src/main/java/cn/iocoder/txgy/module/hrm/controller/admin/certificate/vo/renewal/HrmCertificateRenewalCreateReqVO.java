package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件续证/复审办理 Request VO")
@Data
public class HrmCertificateRenewalCreateReqVO {

    @Schema(description = "证件台账编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "证件台账编号不能为空")
    private Long certificateId;

    @Schema(description = "续证/复审类型，参见 hrm_certificate_renewal_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "续证/复审类型不能为空")
    private Integer type;

    @Schema(description = "办理后证件编号", example = "T44030019900718001X-2029")
    @Size(max = 255, message = "证件编号长度不能超过 255 个字符")
    private String newNo;

    @Schema(description = "办理后有效开始日期")
    private LocalDateTime newStartTime;

    @Schema(description = "办理后有效结束日期")
    private LocalDateTime newEndTime;

    @Schema(description = "复审日期，复审类型必填")
    private LocalDateTime reviewTime;

    @Schema(description = "发证机构", example = "深圳市应急管理局")
    @Size(max = 255, message = "发证机构长度不能超过 255 个字符")
    private String issuingAuthority;

    @Schema(description = "发证日期")
    private LocalDateTime issuingTime;

    @Schema(description = "新证件附件地址")
    @Size(max = 512, message = "新证件附件地址长度不能超过 512 个字符")
    private String attachmentUrl;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
