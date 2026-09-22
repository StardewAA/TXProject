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
}
