package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 证件预警处理 Request VO")
@Data
public class HrmCertificateWarningHandleReqVO {

    @Schema(description = "预警记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "预警记录编号不能为空")
    private Long id;

    @Schema(description = "目标处理状态：已处理(1)、已忽略(2)、重新启用为待处理(0)", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目标处理状态不能为空")
    private Integer status;

    @Schema(description = "处理意见", example = "已通知员工办理续证")
    @Size(max = 500, message = "处理意见长度不能超过 500 个字符")
    private String handleRemark;

}
