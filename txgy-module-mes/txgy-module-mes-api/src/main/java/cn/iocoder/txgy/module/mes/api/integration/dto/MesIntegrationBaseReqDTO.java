package cn.iocoder.txgy.module.mes.api.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPC 服务 - ERP/MES 集成通用请求
 *
 * 字段设计与 ERP→WMS 集成通用请求保持一致，便于 ERP 侧复用同一套出站记录与网关范式。
 */
@Schema(description = "RPC 服务 - ERP/MES 集成通用请求")
@Data
public class MesIntegrationBaseReqDTO {

    @Schema(description = "幂等请求编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;

    @Schema(description = "来源模块", requiredMode = Schema.RequiredMode.REQUIRED, example = "ERP")
    @NotBlank(message = "sourceModule 不能为空")
    private String sourceModule;

    @Schema(description = "业务发生时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "occurredAt 不能为空")
    private LocalDateTime occurredAt;

}
