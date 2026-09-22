package cn.iocoder.txgy.module.hrm.framework.agent.core;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 模型调用响应。
 *
 * <p>只保留文本内容与审计所需的元信息；原始响应报文默认不落库。</p>
 */
@Data
@Accessors(chain = true)
public class HrmRecruitAgentModelResponse {

    /** 模型返回的文本内容，期望为 JSON 字符串 */
    private String content;
    /** 实际使用的模型标识 */
    private String model;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 提示词 token 数；提供方未返回 usage 时为空 */
    private Integer promptTokens;
    /** 输出 token 数 */
    private Integer completionTokens;
    /** 合计 token 数 */
    private Integer totalTokens;
}
