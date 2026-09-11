package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "ERP - Mall shortage procurement demand request")
@Data
public class MallPurchaseDemandReceiveReqVO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "mall-request-20260904-0001")
    @NotBlank(message = "requestId must not be blank")
    private String requestId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MALL-DM-20260904-0001")
    @NotBlank(message = "mallDemandNo must not be blank")
    private String mallDemandNo;

    @NotNull(message = "tenantId must not be null")
    private Long tenantId;
    @NotNull(message = "enterpriseId must not be null")
    private Long enterpriseId;
    private Long shopId;
    private String orderNo;
    private String shopOrderNo;

    @NotNull(message = "warehouseId must not be null")
    private Long warehouseId;
    private LocalDate requiredDeliveryDate;
    private String contactRequirement;
    private String technicalRequirement;
    private String attachmentUrls;

    @Valid
    @NotEmpty(message = "items must not be empty")
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "demandItemId must not be blank")
        private String demandItemId;
        @NotBlank(message = "skuId must not be blank")
        private String skuId;
        @NotBlank(message = "erpMaterialCode must not be blank")
        private String erpMaterialCode;
        @NotBlank(message = "productName must not be blank")
        private String productName;
        private String specification;
        @NotBlank(message = "unitName must not be blank")
        private String unitName;

        @NotNull(message = "orderQuantity must not be null")
        @DecimalMin(value = "0.000001", message = "orderQuantity must be greater than zero")
        private BigDecimal orderQuantity;
        @NotNull(message = "reservedQuantity must not be null")
        @DecimalMin(value = "0", message = "reservedQuantity must not be negative")
        private BigDecimal reservedQuantity;
        @NotNull(message = "shortageQuantity must not be null")
        @DecimalMin(value = "0.000001", message = "shortageQuantity must be greater than zero")
        private BigDecimal shortageQuantity;
    }

}
