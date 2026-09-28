package cn.iocoder.txgy.module.crm.framework.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CRM AI 助手配置
 *
 * 把大模型密钥放在后端，前端只调本系统接口，避免：
 * 1. 密钥被打包进浏览器（泄露风险）；
 * 2. 依赖 Vite 开发代理，打包部署后代理不存在导致 AI 功能失效。
 */
@Component
@ConfigurationProperties(prefix = "txgy.crm.ai")
@Data
public class CrmAiProperties {

    /**
     * 是否启用 AI 意图识别；关闭后前端会退化为关键词匹配
     */
    private Boolean enabled = true;

    /**
     * OpenAI 兼容接口地址，需包含版本段
     * 例如：https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    private String baseUrl;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 模型名
     */
    private String model = "qwen-plus";

    /**
     * 图片识别（多模态 OCR）模型名
     */
    private String visionModel = "qwen-vl-plus";

    /**
     * 意图识别调用超时时间（毫秒）
     */
    private Integer timeout = 15000;

    /**
     * 图片识别调用超时时间（毫秒），多模态识别较慢，单独放宽
     */
    private Integer extractTimeout = 60000;

}
