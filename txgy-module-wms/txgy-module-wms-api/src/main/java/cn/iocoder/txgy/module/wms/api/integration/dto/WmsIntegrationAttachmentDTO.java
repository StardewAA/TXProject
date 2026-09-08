package cn.iocoder.txgy.module.wms.api.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "RPC 服务 - WMS 业务附件")
@Data
public class WmsIntegrationAttachmentDTO {

    @NotNull(message = "fileId 不能为空")
    private Long fileId;

    @NotBlank(message = "附件地址不能为空")
    private String url;

    @NotBlank(message = "附件名称不能为空")
    private String fileName;

    @NotBlank(message = "附件 MIME 类型不能为空")
    private String mimeType;

    private String sha256;

}
