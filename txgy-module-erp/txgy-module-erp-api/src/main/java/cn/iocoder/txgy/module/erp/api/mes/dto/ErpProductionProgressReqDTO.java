package cn.iocoder.txgy.module.erp.api.mes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RPC 服务 - MES 回传生产完工进度的请求
 *
 * MES 工单完工时按「来源销售订单号 + 物料编码」汇总回传累计合格产量，
 * ERP 侧覆盖式写入订单行的已生产数量（produced_count），天然幂等。
 */
@Schema(description = "RPC 服务 - MES 生产完工进度回传请求")
@Data
public class ErpProductionProgressReqDTO {

    @Schema(description = "幂等请求编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;

    @Schema(description = "来源模块", requiredMode = Schema.RequiredMode.REQUIRED, example = "MES")
    @NotBlank(message = "sourceModule 不能为空")
    private String sourceModule;

    @Schema(description = "业务发生时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "occurredAt 不能为空")
    private LocalDateTime occurredAt;

    @Schema(description = "ERP 销售订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "XSDD20260910001")
    @NotBlank(message = "销售订单号不能为空")
    private String saleOrderNo;

    @Schema(description = "物料编码（ERP 权威 material_code）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @Schema(description = "MES 工单编码", example = "WO20260917000001")
    private String workOrderCode;

    @Schema(description = "累计完工合格数量（覆盖式写入，非增量）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "完工数量不能为空")
    private BigDecimal producedQuantity;

    @Schema(description = "工单是否已完工")
    private Boolean finished;

    @Schema(description = "完工时间")
    private LocalDateTime finishTime;

}
