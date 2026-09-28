package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 签发官方 CA 调起参数的请求（契约 R3）。
 *
 * <p>只含"签哪个文件"这一类**非敏感**信息；平台密钥由后端拼装，前端不接触（见 CaShareLaunchService）。
 */
@Schema(description = "管理后台 - 签发官方 CA 调起参数 Request VO")
@Data
public class CaShareLaunchReqVO {

    @Schema(description = "待签章文件地址（官方支持本地路径或文件上传网络路径）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "D:\\docs\\投标文件.pdf")
    @NotBlank(message = "待签章文件地址不能为空")
    private String awaitingSignatureFilePath;

    @Schema(description = "待签章文件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "投标文件.pdf")
    @NotBlank(message = "待签章文件名称不能为空")
    private String awaitingSignatureFileName;

    @Schema(description = "签章后保存路径（不传则由后端按待签路径派生 _sealed）", example = "D:\\docs\\投标文件_sealed.pdf")
    private String signatureFileSavePath;

    @Schema(description = "签章后文件名称（不传则由后端按原名派生 _sealed）", example = "投标文件_sealed.pdf")
    private String signatureFileName;

    @Schema(description = "文件类型：pdf / ofd（缺省 pdf）", example = "pdf")
    private String fileType;
}
