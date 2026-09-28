package cn.iocoder.txgy.module.hrm.framework.agent.core;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 模型调用请求。
 *
 * <p>userPayload 必须是已经过脱敏与白名单过滤的负载，
 * 适配层不做二次脱敏，也不会记录请求内容。</p>
 */
@Data
@Accessors(chain = true)
public class HrmRecruitAgentModelRequest {

    /** 场景，用于选择提示词模板与输出结构 */
    private String scene;
    /** 提示词版本 */
    private String promptVersion;
    /** 系统提示词 */
    private String systemPrompt;
    /** 已脱敏的用户负载 */
    private String userPayload;

    /**
     * 随这条消息一起发给模型的图片，data URL 形式（{@code data:image/jpeg;base64,...}）。
     *
     * <p>为空时用户消息的 content 就是一段纯字符串；有值时改成 OpenAI 兼容的多模态数组。
     * 其余场景一律不传，请求形状保持不变。</p>
     *
     * <p>前端已经压过尺寸（长边 1600px、JPEG），这里不再做大小校验 ——
     * 真要防超大请求该在网关层拦，不该在适配层猜。</p>
     */
    private String imageDataUrl;

    /**
     * 本次调用是否要求模型返回 JSON 对象。
     *
     * <p>为空时沿用全局配置 {@code hrm.agent.model.json-mode}。结构化抽取类场景
     * （诊断、匹配、优化、生成）都依赖严格 JSON，全局默认是开启的；自由对话
     * 要的是自然语言，必须显式传 false，否则模型会被逼着把一段话塞进 JSON 里。</p>
     */
    private Boolean jsonOutput;
}
