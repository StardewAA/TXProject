package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * **官方 CA 互认签章客户端回调 Request VO**——字段与官方规范**逐字对齐**。
 *
 * <p>依据：官方《签章验签工具接入指南》§4.2.1「客户端签章工具回调接口说明」
 * （docs/接口互认/07-签章验签工具接入指南-交易系统侧.md）。
 *
 * <p>与 H5 版（{@link CaShareSealCallbackReqVO}）的区别：**客户端回调是扁平的**（无 action/data 包裹），
 * 顶层直接是 tradingSystemCode / callbackRequestType / completedSignatureTime / document[] / 机器指纹。
 * 两者都按官方结构原样定义，不做"统一简化"——结构不一致就反序列化不到。
 *
 * <p>官方请求示例（原文）：
 * <pre>
 * { "tradingSystemCode": "交易系统编码", "callbackRequestType": "回调类型",
 *   "completedSignatureTime": "完成签章时间",
 *   "document": [ { "awaitingSignatureFileName": "...", "signatureFileName": "...",
 *     "awaitingSignatureFileHash": "...", "signatureFileHash": "...", "fileTotalPages": "...",
 *     "fileTotalSealCount": "...", "fileCurrentSealCount": "...", "fileSignatureStatus": "已签章已验章" } ],
 *   "ipAddress": "ip 地址", "macAddress": "mac 地址", "cpuSn": "cpu 序列号", "hardDiskSn": "硬盘序列号" }
 * </pre>
 */
@Schema(description = "官方 CA 互认签章客户端回调 Request VO（字段对齐官方 §4.2.1）")
@Data
public class CaShareSealClientCallbackReqVO {

    @Schema(description = "交易系统编码", example = "C1100005553")
    private String tradingSystemCode;

    @Schema(description = "回调类型：exitSignature 退出程序 / signedAndVerified 完成签章与验", example = "signedAndVerified")
    private String callbackRequestType;

    @Schema(description = "完成签章时间", example = "2024-11-24 15:49:01")
    private String completedSignatureTime;

    @Schema(description = "文档信息列表")
    private List<DocumentItem> document;

    @Schema(description = "ip 地址")
    private String ipAddress;
    @Schema(description = "mac 地址")
    private String macAddress;
    @Schema(description = "cpu 序列号")
    private String cpuSn;
    @Schema(description = "硬盘序列号")
    private String hardDiskSn;

    /** 文档信息（官方 document 数组元素；客户端版与 H5 版字段一致）。 */
    @Schema(description = "文档信息")
    @Data
    public static class DocumentItem {

        @Schema(description = "待签章文件名称")
        private String awaitingSignatureFileName;
        @Schema(description = "完成签章文件名称")
        private String signatureFileName;
        @Schema(description = "待签章文件 HASH")
        private String awaitingSignatureFileHash;
        @Schema(description = "已签章文件 Hash")
        private String signatureFileHash;
        @Schema(description = "文件总页码")
        private Integer fileTotalPages;
        @Schema(description = "文件签章总数量")
        private Integer fileTotalSealCount;
        @Schema(description = "文件本次签章数量")
        private Integer fileCurrentSealCount;
        @Schema(description = "文件签署状态：1 已签章已验章 / 2 已签章未验章 / 3 未签章", example = "1")
        private String fileSignatureStatus;
    }
}
