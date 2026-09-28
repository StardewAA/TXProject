package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentChatRespVO;

/**
 * 求职助理自由对话。
 *
 * <p>与其它 Agent 场景不同，这里不建任务、不落库、不做结构化校验：
 * 它就是一次问答，一问一答，回复即用即弃。</p>
 */
public interface HrmRecruitAgentChatService {

    /**
     * 就求职者的一句话给出回复。
     *
     * @param userId       登录用户编号，用于挡住匿名调用（该端点会消耗模型额度）
     * @param message      求职者说的话，可能包含前端拼进来的附件正文
     * @param imageDataUrl 随消息一起发的图片（data URL）；为空表示纯文字
     * @return 模型回复
     */
    HrmPortalRecruitAgentChatRespVO chat(Long userId, String message, String imageDataUrl);
}
