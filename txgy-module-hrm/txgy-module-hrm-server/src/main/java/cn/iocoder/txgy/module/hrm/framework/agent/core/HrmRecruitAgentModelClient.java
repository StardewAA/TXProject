package cn.iocoder.txgy.module.hrm.framework.agent.core;

/**
 * 招聘 Agent 模型适配层。
 *
 * <p>HRM 只依赖这个接口，不直接依赖任何具体模型 SDK，
 * 也不修改其它模块；更换模型或接入方式只需替换实现。</p>
 *
 * <p>实现约定：
 * <ul>
 *   <li>未配置模型时抛「模型尚未配置」，绝不返回编造内容；</li>
 *   <li>不记录请求负载与密钥；</li>
 *   <li>失败时抛出可映射到明确错误码的异常，由调用方标记任务失败。</li>
 * </ul></p>
 */
public interface HrmRecruitAgentModelClient {

    /**
     * 当前是否已具备调用条件。未就绪时业务侧应直接给出明确提示，而不是尝试调用。
     */
    boolean isReady();

    /**
     * 当前模型标识，用于审计与结果记录。
     */
    String modelName();

    /**
     * 发起一次文本生成调用。
     *
     * @param request 已脱敏的请求
     * @return 模型响应
     */
    HrmRecruitAgentModelResponse chat(HrmRecruitAgentModelRequest request);
}
