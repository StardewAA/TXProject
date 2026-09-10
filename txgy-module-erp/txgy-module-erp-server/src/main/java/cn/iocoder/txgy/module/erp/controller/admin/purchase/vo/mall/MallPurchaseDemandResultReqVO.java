package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "ERP - Mall shortage procurement demand result update")
@Data
public class MallPurchaseDemandResultReqVO {

    @Schema(description = "ERP processing result", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DELAYED", "PURCHASE_COMPLETED", "PURCHASE_FAILED", "CANCELLED", "CANCEL_REJECTED"})
    @NotBlank(message = "status must not be blank")
    private String status;

    @Schema(description = "Linked ERP purchase order number, optional when multiple purchase orders are involved")
    private String erpOrderNo;

    @Schema(description = "Business reason code")
    private String reasonCode;

    @Schema(description = "Business result or reason message")
    private String message;

    @Schema(description = "Latest estimated arrival date")
    private LocalDate estimatedArrivalDate;

}
