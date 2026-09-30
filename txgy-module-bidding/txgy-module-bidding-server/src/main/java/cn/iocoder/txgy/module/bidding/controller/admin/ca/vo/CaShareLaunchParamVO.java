package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * **官方 CA 互认签章「调起参数」VO**——字段与官方规范**逐字对齐**（含大小写，官方有拼写瑕疵不改）。
 *
 * <p>依据：官方《签章验签工具接入指南》§5.3.1「H5 页面(浏览器)调起 CA 互认检测安装工具 入参示例」
 * （docs/接口互认/07-签章验签工具接入指南-交易系统侧.md）。
 *
 * <p><b>用途</b>：交易系统把这些参数交给官方 CA 互认检测安装工具 → 工具校验通过后拉起
 * CA 互认签章客户端 → 用户扫码签章 → 客户端/组件按 {@code tradingSystemConfig.callBackUrl} 回调本系统。
 *
 * <p><b>字段名严格照抄官方</b>（含以下易错点，改名即对接失败）：
 * <ul>
 *   <li>{@code CAShareUseScene} —— 大驼峰，非 camelCase；</li>
 *   <li>{@code ClientSercet}（官方原文拼写，正确拼写应为 Secret）—— 文档表格与 JSON 示例不一致，
 *       此处取**示例 JSON 的 {@code clientSecret}**（示例是可直接运行的代码，优先于文档表格），
 *       并同时保留 {@code ClientSercet} 字段以兼容按表格实现的调用方（详见类内注释）；</li>
 *   <li>{@code callBackUrl} —— 官方写作 callBack（大写 B）。**同类不一致**：§5.3 参数表写
 *       {@code callbackRequestUrl}，§5.3.1 JSON 示例写 {@code callBackUrl}；
 *       此处两个字段都收（同 {@code clientSecret}/{@code ClientSercet} 的处理方式）。</li>
 * </ul>
 *
 * <p><b>通用原则</b>：官方文档的「参数表」与「示例 JSON」存在多处拼写/命名不一致，
 * 在无法实测校验前，**两种写法都保留**，由调用方按自己参考的那份文档填其一，
 * 不擅自"纠正"官方写法（纠正即对接失败）。
 */
@Schema(description = "官方 CA 互认签章调起参数（字段对齐官方 §5.3.1）")
@Data
public class CaShareLaunchParamVO {

    @Schema(description = "使用场景：00 文件签章 / 01 文件加密（签章传 00）", example = "00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String CAShareUseScene;

    @Schema(description = "文件签章/加密参数")
    private KeyParameter keyParameter;

    @Schema(description = "CA 共享组件配置")
    private CaShareComponentConfig caShareComponentConfig;

    @Schema(description = "交易系统配置（含回调地址）")
    private TradingSystemConfig tradingSystemConfig;

    /** 文件签章参数（官方 keyParameter）。 */
    @Schema(description = "文件签章参数")
    @Data
    public static class KeyParameter {

        @Schema(description = "文件信息列表")
        private List<DocumentItem> document;

        @Schema(description = "印章配置")
        private SealConfig seal;

        /** 文件信息（官方 document 数组元素）。 */
        @Schema(description = "文件信息")
        @Data
        public static class DocumentItem {

            @Schema(description = "待签章文件地址（支持本地路径或文件上传网络路径）", requiredMode = Schema.RequiredMode.REQUIRED)
            private String awaitingSignatureFilePath;
            @Schema(description = "待签章文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
            private String awaitingSignatureFileName;
            @Schema(description = "签章后文件的保存路径（支持本地路径或文件上传网络路径）", requiredMode = Schema.RequiredMode.REQUIRED)
            private String signatureFileSavePath;
            @Schema(description = "签章后文件的名称", requiredMode = Schema.RequiredMode.REQUIRED)
            private String signatureFileName;
            @Schema(description = "文件类型：pdf / ofd（默认传 pdf）", example = "pdf", requiredMode = Schema.RequiredMode.REQUIRED)
            private String awaitingSignatureFileType;
        }

        /** 印章配置（官方 seal）。 */
        @Schema(description = "印章配置")
        @Data
        public static class SealConfig {

            @Schema(description = "印章图片 DPI（默认 96）", example = "96")
            private Integer dpi;
            @Schema(description = "印章透明度（默认 0.8）", example = "0.8")
            private Double transparency;
            @Schema(description = "印章图片最大宽高（默认 150）", example = "150")
            private Integer sealImgMaxValue;
        }
    }

    /** CA 共享组件配置（官方 caShareComponentConfig）。 */
    @Schema(description = "CA 共享组件配置")
    @Data
    public static class CaShareComponentConfig {

        @Schema(description = "交易系统编码", requiredMode = Schema.RequiredMode.REQUIRED)
        private String tradingSystemCode;
        @Schema(description = "CA 互认共享组件服务地址", requiredMode = Schema.RequiredMode.REQUIRED)
        private String serviceUrl;
        @Schema(description = "客户端 ID（用户名）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String clientId;
        @Schema(description = "客户端密钥（官方 JSON 示例字段名）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String clientSecret;
        @Schema(description = "客户端密钥（官方文档表格字段名 ClientSercet；与 clientSecret 同义，"
                + "按表格实现的调用方传本字段，二者填其一即可）")
        private String ClientSercet;
        @Schema(description = "签名密钥（accessKeySecret）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String accessKeySecret;
    }

    /** 交易系统配置（官方 tradingSystemConfig）。 */
    @Schema(description = "交易系统配置")
    @Data
    public static class TradingSystemConfig {

        @Schema(description = "回调地址（官方 §5.3.1 JSON 示例写法 callBackUrl，大写 B）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String callBackUrl;

        @Schema(description = "回调地址（官方 §5.3 参数表写法 callbackRequestUrl；与 callBackUrl 同义，"
                + "文档表格与示例不一致，二者填其一即可）")
        private String callbackRequestUrl;
    }
}
