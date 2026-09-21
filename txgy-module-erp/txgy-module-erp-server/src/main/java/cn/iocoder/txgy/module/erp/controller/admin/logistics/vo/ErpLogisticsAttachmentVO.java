package cn.iocoder.txgy.module.erp.controller.admin.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "ERP - 物流附件元数据")
@Data
public class ErpLogisticsAttachmentVO {

    @NotNull(message = "fileId 不能为空")
    private Long fileId;
    @NotBlank(message = "附件 URL 不能为空")
    @Pattern(regexp = "^https?://.+", message = "附件 URL 必须使用 HTTP 或 HTTPS")
    private String url;
    @NotBlank(message = "附件名称不能为空")
    private String fileName;
    private String mimeType;
    private String sha256;

}
