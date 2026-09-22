package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理后台 - 质保档案状态修正 Request VO
 *
 * <p>自动流转是单向的（已到期终止不会被回退），当到期日被人工调整、
 * 或业务上需要提前终止质保时，由管理员通过本接口手动纠正状态。
 *
 * @author txgycloud
 */
@Schema(description = "管理后台 - 质保档案状态修正 Request VO")
@Data
@Accessors(chain = true)
public class CrmAfterSaleWarrantyUpdateStatusReqVO {

    @Schema(description = "质保档案编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "质保档案编号不能为空")
    private Long id;

    @Schema(description = "目标质保状态：1质保中 2即将到期 3已到期终止",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "质保状态不能为空")
    private Integer status;

    @Schema(description = "调整原因（写入日志，便于审计）", example = "合同质保期已延长至 2028-09-01，状态回退")
    private String reason;

}
