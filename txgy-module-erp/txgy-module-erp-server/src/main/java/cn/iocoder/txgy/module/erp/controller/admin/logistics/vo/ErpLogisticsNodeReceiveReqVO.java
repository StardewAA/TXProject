package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpLogisticsNodeReceiveReqVO {

    @NotBlank(message = "requestId 不能为空")
    private String requestId;
    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;
    @NotBlank(message = "logisticsTaskNo 不能为空")
    private String logisticsTaskNo;
    @NotBlank(message = "waybillNo 不能为空")
    private String waybillNo;
    @NotBlank(message = "nodeCode 不能为空")
    private String nodeCode;
    @NotBlank(message = "nodeName 不能为空")
    private String nodeName;
    @NotNull(message = "occurredAt 不能为空")
    private LocalDateTime occurredAt;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationDescription;
    private String remark;
    @Valid
    private List<ErpLogisticsAttachmentVO> attachments;
    private String reportSource;
    private Boolean locationOnline;
    private LocalDateTime lastOnlineTime;
    @NotNull(message = "eventVersion 不能为空")
    @Positive(message = "eventVersion 必须大于 0")
    private Long eventVersion;

}
