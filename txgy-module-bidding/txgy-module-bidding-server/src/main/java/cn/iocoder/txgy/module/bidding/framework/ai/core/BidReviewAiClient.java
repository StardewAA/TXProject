package cn.iocoder.txgy.module.bidding.framework.ai.core;

/**
 * 智慧标书审查 · 大模型直连客户端（接口）。
 *
 * <p>方案依据：§2.4、§6.4。做成接口是为了"将来若改回统一走平台 AI 模块 / 换供应商，
 * 只换实现类与配置，不动流水线"。
 */
public interface BidReviewAiClient {

    /** 是否已启用且配置齐全（未配置时调用方走降级，不抛异常）。 */
    boolean isAvailable();

    /** 当前模型名（写流水与报告标注用）。 */
    String model();

    /** 掩码密钥（配置页回显用）。 */
    String maskedApiKey();

    /**
     * 调用模型并返回 JSON 文本（已剥离 markdown code fence）。
     *
     * @param taskId       任务 id（写流水；自检场景可为 null）
     * @param step         步骤（RULE/GENERATE/REPORT/PROBE）
     * @param systemPrompt 系统提示词（必须显式要求只输出 JSON，且出现 "json" 字样）
     * @param userPrompt   用户提示词（规则集 + 标书相关片段）
     * @return 模型返回的 JSON 文本
     */
    String chatJson(Long taskId, String step, String systemPrompt, String userPrompt);

    /** 最小连通性自检（管理端"测试连接"按钮，不写业务数据）。 */
    String probe();
}
