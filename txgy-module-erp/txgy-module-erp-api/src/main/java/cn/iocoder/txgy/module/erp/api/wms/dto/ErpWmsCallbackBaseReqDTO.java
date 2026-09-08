package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpWmsCallbackBaseReqDTO {

    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;

    @NotBlank(message = "businessChainNo 不能为空")
    private String businessChainNo;

    @NotNull(message = "eventVersion 不能为空")
    private Long eventVersion;

    @NotNull(message = "occurredAt 不能为空")
    private LocalDateTime occurredAt;

}
