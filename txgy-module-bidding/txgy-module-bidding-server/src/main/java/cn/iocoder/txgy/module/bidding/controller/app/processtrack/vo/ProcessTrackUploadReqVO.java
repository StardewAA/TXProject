package cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 递交加密投标文件 Request（CA 加密 + SM3 指纹 + SM2 签名由外部 CA SDK 完成）")
@Data
public class ProcessTrackUploadReqVO {

    @Schema(description = "投标流程跟踪ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流程跟踪ID不能为空")
    private Long id;

    @Schema(description = "投标员工用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "投标文件（加密后）fileId", requiredMode = Schema.RequiredMode.REQUIRED, example = "6082")
    @NotNull(message = "投标文件 fileId 不能为空")
    private Long documentFileId;

    @Schema(description = "投标文件名", example = "XX公司投标文件.pdf")
    private String documentName;

    @Schema(description = "投标文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED, example = "204800")
    @NotNull(message = "投标文件大小不能为空")
    private Long documentSize;

    @Schema(description = "投标文件指纹（SM3 十六进制，国密 GB/T 32905）", requiredMode = Schema.RequiredMode.REQUIRED, example = "66c7f0f462eeedd9...")
    @NotBlank(message = "投标文件 SM3 指纹不能为空")
    private String documentHash;

    @Schema(description = "投标文件数字签名（CA SDK 签名，SM2 base64）", requiredMode = Schema.RequiredMode.REQUIRED, example = "MEUCIQ...")
    @NotBlank(message = "投标文件数字签名不能为空")
    private String documentSignature;

    @Schema(description = "CA 证书序列号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SN-9E8D7C")
    @NotBlank(message = "CA 证书序列号不能为空")
    private String caCertNo;

    @Schema(description = "CA 公钥（PEM，验签用）", example = "-----BEGIN PUBLIC KEY-----...")
    private String caPublicKey;

    @Schema(description = "指纹算法（契约 A9；可空，缺省并强制 SM3）。传非 SM3 值将被拒绝，"
            + "避免证据链里出现不可信的算法标识", example = "SM3")
    private String documentHashAlgo;
}
