package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP 供应商风险事件新增 Request VO")
@Data
public class ErpSupplierRiskEventSaveReqVO {

    @NotNull(message = "供应商编号不能为空")
    private Long supplierId;
    @NotBlank(message = "风险事件类型不能为空")
    private String eventType;
    private Integer severity;
    private Boolean blocksPurchase;
    private LocalDateTime occurredAt;
    private String description;
}
