package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** CA H5 签章完成回调（本地测试签章与真实组件回调统一口径）。 */
@Schema(description = "管理后台 - CA 文件签章完成回调 Request VO")
@Data
public class SealCallbackReqVO {

    @NotBlank(message = "业务编码不能为空")
    @Schema(description = "业务编码；投标文件签章时传 bidding_process_track.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String businessCode;

    @NotBlank(message = "签章 documentId 不能为空")
    @Schema(description = "签章 documentId", requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentId;

    @NotBlank(message = "签名值不能为空")
    @Schema(description = "SM2 签名值（base64）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signature;

    @NotBlank(message = "CA 证书序列号不能为空")
    @Schema(description = "CA 证书序列号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String certSn;

    @NotBlank(message = "文件 SM3 指纹不能为空")
    @Schema(description = "文件 SM3 指纹", requiredMode = Schema.RequiredMode.REQUIRED)
    private String hash;

    @Schema(description = "文件 SHA-256（当前表结构未落 document_sha256 列，随签名文件归档）")
    private String sha256;

    @Schema(description = "签章后文件 fileId")
    private Long sealFileId;

    @Schema(description = "签章后文件 URL")
    private String sealFileUrl;

    @Schema(description = "CA 公钥 PEM；不传则使用统一测试账号公钥")
    private String caPublicKey;
}
