package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件电子签章 - 签章 document 签发 Response VO。
 *
 * <p>对齐 30 号文档（CA互认H5签章组件）document 参数语义：前端用 networkPath + 回调地址装载
 * H5 签章组件；测试配置（enabled=false）下签章在本地真实完成（SM2 签名 + PDF 盖章/分离式签名信息文件），
 * signature/certSn/hash/sealFile* 即为真实签章产物。
 */
@Schema(description = "管理后台 - CA 文件签章 document 签发 Response VO")
@Data
public class SealDocumentRespVO {

    @Schema(description = "签章会话 documentId", example = "SEAL-DOC-9f2c...")
    private String documentId;

    @Schema(description = "交易平台代码（tradingSystemCode；测试配置缺省 axj11111111）", example = "axj11111111")
    private String tradingSystemCode;

    @Schema(description = "原文件网络路径（H5 组件装载 document.networkPath）", example = "http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/xxx.pdf")
    private String networkPath;

    @Schema(description = "签章回调地址（document.callbackUrl）")
    private String callbackUrl;

    @Schema(description = "SM2 签名值（base64；对文件 SM3 指纹签名，真实可验）")
    private String signature;

    @Schema(description = "CA 证书序列号（统一测试账号 SN-LOCAL-CA-TEST-0001）", example = "SN-LOCAL-CA-TEST-0001")
    private String certSn;

    @Schema(description = "文件 SM3 指纹（document_hash）", example = "728fac...")
    private String hash;

    @Schema(description = "文件 SHA-256")
    private String sha256;

    @Schema(description = "签章单位（统一测试账号）", example = "本地CA测试单位")
    private String orgName;

    @Schema(description = "签章时间", example = "2026-09-19 09:50:00")
    private String sealTime;

    @Schema(description = "签章后文件 fileId（PDF=已盖章 PDF；其他=签名信息 .sig.txt）")
    private Long sealFileId;

    @Schema(description = "签章后文件 URL")
    private String sealFileUrl;

    @Schema(description = "签章后文件名")
    private String sealFileName;

    @Schema(description = "签章后文件字节数（契约 A1：前端可直接填入递交入参 documentSize，无需二次回查）")
    private Long sealFileSize;

    @Schema(description = "关联投标流程跟踪 id（businessCode 可解析时；落库成功非 null）")
    private Long trackId;
}
