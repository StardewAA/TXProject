package cn.iocoder.txgy.module.ai.api.model;

import lombok.Data;

/**
 * AI 聊天模型连接信息 DTO
 *
 * <p>给「非 AI 模块」的业务系统（如 WMS 的自研 AI 客服）使用：这些系统直接用
 * HTTP 调用大模型，拿不到 Spring AI 的 ChatModel，因此需要把「地址 + Key + 模型名」
 * 一次取回去。
 *
 * <p>地址已由 AI 模块按平台规则补全为 OpenAI 兼容的 chat/completions 端点。
 */
@Data
public class AiChatModelConnDTO {

    /** 平台标识，如 DouBao / DeepSeek / SiliconFlow */
    private String platform;

    /** 平台中文名 */
    private String platformName;

    /** OpenAI 兼容的 chat/completions 完整地址 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型标识（传给大模型的 model 字段） */
    private String model;

    /** 模型显示名 */
    private String name;

}
