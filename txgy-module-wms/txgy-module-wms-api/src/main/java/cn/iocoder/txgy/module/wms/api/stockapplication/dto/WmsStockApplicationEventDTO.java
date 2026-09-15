package cn.iocoder.txgy.module.wms.api.stockapplication.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class WmsStockApplicationEventDTO {
    @NotBlank private String eventId;
    @NotNull private Long tenantId;
    @NotBlank private String applicationNo;
    @NotBlank private String sourceNo;
    @NotBlank private String businessChainNo;
    @NotBlank private String type;
    @NotNull private Long version;
    @NotBlank private String approvalStatus;
    @NotBlank private String executionStatus;
}
