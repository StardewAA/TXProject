package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpLogisticsReceiptReceiveReqVO {

    @NotBlank(message = "requestId 不能为空")
    private String requestId;
    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;
    @NotBlank(message = "logisticsTaskNo 不能为空")
    private String logisticsTaskNo;
    @NotBlank(message = "sourceOrderNo 不能为空")
    private String sourceOrderNo;
    @NotBlank(message = "receiptType 不能为空")
    private String receiptType;
    @NotBlank(message = "receiverName 不能为空")
    private String receiverName;
    @NotNull(message = "receiptTime 不能为空")
    private LocalDateTime receiptTime;
    @NotNull(message = "receiptQuantity 不能为空")
    @DecimalMin(value = "0.000001", message = "receiptQuantity 必须大于 0")
    private BigDecimal receiptQuantity;
    private String remark;
    @Valid
    private List<ErpLogisticsAttachmentVO> attachments;
    @NotNull(message = "eventVersion 不能为空")
    @Positive(message = "eventVersion 必须大于 0")
    private Long eventVersion;

}
