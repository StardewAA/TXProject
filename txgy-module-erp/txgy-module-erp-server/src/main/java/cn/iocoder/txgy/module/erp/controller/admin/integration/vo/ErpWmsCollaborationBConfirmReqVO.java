package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP 协作单 B 业务确认 Request VO")
@Data
public class ErpWmsCollaborationBConfirmReqVO {

    @NotNull(message = "协作单编号不能为空")
    private Long id;

    @NotBlank(message = "确认动作不能为空")
    @Schema(description = "确认动作：ACCEPTED / REJECTED")
    private String action;

    private Long supplierId;
    private Long accountId;
    private LocalDateTime orderTime;
    private String reason;
    private String remark;

    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "行号不能为空")
        private Integer lineNo;

        @NotNull(message = "采购单价不能为空")
        @DecimalMin(value = "0.00", message = "采购单价不能小于 0")
        private BigDecimal productPrice;

        @DecimalMin(value = "0.00", message = "税率不能小于 0")
        private BigDecimal taxPercent;

        private String remark;
    }
}
