package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * **官方 CA 互认签章组件（H5）回调 Request VO**——字段与官方规范**逐字对齐**。
 *
 * <p>依据：《移动数字证书(CA)互认技术标准 签章验签工具接入指南 电子招标投标交易系统/交易工具侧 20241212》§4.2.2
 * （docs/接口互认/07-签章验签工具接入指南-交易系统侧.md）。
 *
 * <p>为什么必须逐字对齐：官方组件按固定 JSON 结构 POST 回调，**字段名/嵌套层级/类型任一不符即对接失败**
 * （无法反序列化 → 回调丢失 → 签章结果不入账）。故本 VO 不做任何"合理简化"或改名。
 *
 * <p>请求示例（官方原文）：
 * <pre>
 * { "action": "allDone", "data": { "awaitingSignatureFileId": "...", "sourceFileKey": "...",
 *   "status": "20", "targetFileKey": "sign/202411/24/final_xxx.pdf", "time": "20241124154901",
 *   "transactionId": "...", "userId": "userId",
 *   "signatureInfo": { "callbackRequestType": "完成签章", "completedSignatureTime": "2024-11-24 15:49:01",
 *     "tradingSystemCode": "C1100005553",
 *     "document": [ { "awaitingSignatureFileHash": "...", "awaitingSignatureFileName": "...",
 *       "fileCurrentSealCount": 2, "fileSignatureStatus": "已签章已验章", "fileTotalPages": 98,
 *       "fileTotalSealCount": 135, "signatureFileHash": "...", "signatureFileName": "sign/..." } ] } } }
 * </pre>
 */
@Schema(description = "官方 CA 互认签章组件（H5）回调 Request VO（字段对齐官方 §4.2.2）")
@Data
public class CaShareSealCallbackReqVO {

    @Schema(description = "操作动作：sign 单词签章 / allDone 本批操作全部完成", example = "allDone")
    private String action;

    @Schema(description = "回调详细数据")
    private CallbackData data;

    /** 回调详细数据（官方 data 对象）。 */
    @Schema(description = "回调详细数据")
    @Data
    public static class CallbackData {

        @Schema(description = "待签文件 Id（交易系统调起签章组件时传入，回调原样传回）")
        private String awaitingSignatureFileId;
        @Schema(description = "源文件在对象存储中的 FileKey")
        private String sourceFileKey;
        @Schema(description = "源文件文件存储类型", example = "s3")
        private String sourceFileStorage;
        @Schema(description = "操作状态：10 进行中 / 20 已完成 / 30 失败", example = "20")
        private String status;
        @Schema(description = "目标文件在对象存储中的 FileKey")
        private String targetFileKey;
        @Schema(description = "目标文件文件存储类型", example = "s3")
        private String targetFileStorage;
        @Schema(description = "操作时间（官方格式 yyyyMMddHHmmss）", example = "20241124154901")
        private String time;
        @Schema(description = "交易系统编码（调起时传入，回调原样传回）", example = "C1100005553")
        private String tradingSystemCode;
        @Schema(description = "签章组件内部业务流水号")
        private String transactionId;
        @Schema(description = "用户 Id（调起时传入，回调原样传回）")
        private String userId;

        @Schema(description = "签章信息描述（含 callbackRequestType / completedSignatureTime / tradingSystemCode / document）")
        private SignatureInfo signatureInfo;

        /** 签章信息描述（官方 signatureInfo 对象）。 */
        @Schema(description = "签章信息描述")
        @Data
        public static class SignatureInfo {

            @Schema(description = "回调类型，如：完成签章", example = "完成签章")
            private String callbackRequestType;
            @Schema(description = "完成签章时间，格式 yyyy-MM-dd HH:mm:ss", example = "2024-11-24 15:49:01")
            private String completedSignatureTime;
            @Schema(description = "交易系统编码", example = "C1100005553")
            private String tradingSystemCode;
            @Schema(description = "文档信息列表")
            private List<DocumentItem> document;

            /** 文档信息（官方 document 数组元素）。 */
            @Schema(description = "文档信息")
            @Data
            public static class DocumentItem {

                @Schema(description = "待签名文件 Hash")
                private String awaitingSignatureFileHash;
                @Schema(description = "待签名文件名称")
                private String awaitingSignatureFileName;
                @Schema(description = "本次签章个数（进入组件至离开组件期间新盖的所有章）")
                private Integer fileCurrentSealCount;
                @Schema(description = "文件签署状态，如：已签章已验章", example = "已签章已验章")
                private String fileSignatureStatus;
                @Schema(description = "文件总页数")
                private Integer fileTotalPages;
                @Schema(description = "总印章数量")
                private Integer fileTotalSealCount;
                @Schema(description = "签后文件 hash")
                private String signatureFileHash;
                @Schema(description = "签后文件名称（对象存储 FileKey）")
                private String signatureFileName;
            }
        }
    }
}
