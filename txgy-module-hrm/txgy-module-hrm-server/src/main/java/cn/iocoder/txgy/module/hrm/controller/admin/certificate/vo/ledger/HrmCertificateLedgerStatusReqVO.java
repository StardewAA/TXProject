package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - HRM 证件状态变更 Request VO")
@Data
public class HrmCertificateLedgerStatusReqVO {

    @Schema(description = "证件台账编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "证件台账编号不能为空")
    private Long id;

    @Schema(description = "目标状态：注销(4)、恢复为有效(1)", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "目标状态不能为空")
    private Integer status;

}
