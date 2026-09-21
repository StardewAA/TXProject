package cn.iocoder.txgy.module.mes.api.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * RPC 服务 - MES 主数据接入的应答
 */
@Schema(description = "RPC 服务 - MES 主数据接入应答")
@Data
@Accessors(chain = true)
public class MesIntegrationAcceptedRespDTO {

    @Schema(description = "是否已被接受")
    private Boolean accepted;

    @Schema(description = "幂等请求编号")
    private String requestId;

    @Schema(description = "MES 侧落库后的业务编码（物料编码 / 往来企业编码），新建时回填")
    private String targetCode;

    @Schema(description = "处理结果，CREATED / UPDATED")
    private String businessStatus;

    @Schema(description = "已处理的数据版本")
    private Long processedVersion;

}
