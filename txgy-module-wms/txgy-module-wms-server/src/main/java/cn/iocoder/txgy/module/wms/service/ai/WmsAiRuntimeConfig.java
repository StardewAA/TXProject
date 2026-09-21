package cn.iocoder.txgy.module.wms.service.ai;

import lombok.Data;

/**
 * WMS AI 运行期生效的模型配置
 *
 * <p>由 {@link WmsAiConfigService#resolveRuntimeConfig()} 组装：模型名、调用地址、Key。
 * 来源可能是「界面配置（wms_ai_config + ai_model + ai_api_key）」或「容器环境变量」，
 * 通过 {@link #source} 区分，便于排查配置为何不生效。
 */
@Data
public class WmsAiRuntimeConfig {

    /** 生效来源 */
    public enum Source {
        /** 界面配置（wms_ai_config） */
        CONFIG,
        /** 容器环境变量 txgy.ai.siliconflow.* */
        ENV,
        /** 都没配 */
        NONE,
    }

    private Source source = Source.NONE;

    /** OpenAI 兼容的 chat/completions 地址 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 文本模型名 */
    private String model;

    /** 视觉模型名（图片识别用）；界面配置时与 model 相同，取平台的默认 */
    private String visionModel;

    /** thinking 参数（部分厂商用于关闭思考链） */
    private String thinkingType;

    /** 超时秒数 */
    private int timeoutSeconds = 240;

    /** 是否可用（baseUrl + apiKey + model 均非空） */
    public boolean usable() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

}
