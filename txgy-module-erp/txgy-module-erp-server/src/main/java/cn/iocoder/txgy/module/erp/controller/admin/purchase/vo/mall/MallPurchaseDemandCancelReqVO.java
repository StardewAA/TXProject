package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "ERP - Mall shortage procurement demand cancellation request")
@Data
public class MallPurchaseDemandCancelReqVO {

    @NotBlank(message = "cancelReason must not be blank")
    private String cancelReason;

}
