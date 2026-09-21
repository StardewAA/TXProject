package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpLogisticsTaskReceiveReqVO {

    @NotBlank(message = "requestId 不能为空")
    private String requestId;
    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;
    @NotBlank(message = "businessChainNo 不能为空")
    private String businessChainNo;
    @NotBlank(message = "logisticsTaskNo 不能为空")
    private String logisticsTaskNo;
    @NotBlank(message = "waybillNo 不能为空")
    private String waybillNo;
    @NotBlank(message = "sourceOrderType 不能为空")
    private String sourceOrderType;
    @NotBlank(message = "sourceOrderNo 不能为空")
    private String sourceOrderNo;
    private String carrierCode;
    @NotBlank(message = "carrierName 不能为空")
    private String carrierName;
    private String vehicleNo;
    private String driverName;
    private String driverPhoneMasked;
    private LocalDateTime plannedDepartureTime;
    private LocalDateTime estimatedArrivalTime;
    @Pattern(regexp = "^https?://.+", message = "轨迹 URL 必须使用 HTTP 或 HTTPS")
    private String trackUrl;
    @NotNull(message = "eventVersion 不能为空")
    @Positive(message = "eventVersion 必须大于 0")
    private Long eventVersion;
    @Valid
    @NotEmpty(message = "items 不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "lineNo 不能为空")
        @Positive(message = "lineNo 必须大于 0")
        private Integer lineNo;
        private String sourceItemNo;
        private Long productId;
        private String materialCode;
        @NotBlank(message = "itemName 不能为空")
        private String itemName;
        private String specification;
        @NotBlank(message = "unitName 不能为空")
        private String unitName;
        @NotNull(message = "quantity 不能为空")
        @DecimalMin(value = "0.000001", message = "quantity 必须大于 0")
        private BigDecimal quantity;
    }

}
