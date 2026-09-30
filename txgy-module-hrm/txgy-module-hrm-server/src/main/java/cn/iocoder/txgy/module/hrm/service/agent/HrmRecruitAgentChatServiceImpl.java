package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentChatRespVO;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelClient;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelRequest;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelResponse;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_NOT_CONFIGURED;

/**
 * 求职助理自由对话实现。
 *
 * <p>刻意不复用 {@link HrmRecruitAgentSceneRunner}：那条链路是为「异步任务 + 结构化 JSON +
 * 落库留痕」设计的，走一遍要建任务、轮询、校验 JSON。一次问答用不上这些，
 * 硬套反而会把自然语言回复塞进 JSON 校验里直接失败。</p>
 */
@Slf4j
@Service
public class HrmRecruitAgentChatServiceImpl implements HrmRecruitAgentChatService {

    /** 场景标识。模型适配层当前不读它，只用于日志与将来排查 */
    private static final String SCENE = "AGENT_CHAT";

    /**
     * 模型在回答末尾加的「这条在列岗位」标记。
     *
     * <p>服务端摘掉之后才返回前端。用标记而不是让模型输出 JSON：这个端点走的是
     * 自由对话（json-mode 必须关掉），让它吐结构化数据会牺牲自然语气。</p>
     */
    private static final String JOB_MARKER = "[JOBS]";

    @Resource
    private HrmRecruitAgentModelClient modelClient;
    @Resource
    private HrmRecruitAgentJobContextBuilder jobContextBuilder;

    @Override
    public HrmPortalRecruitAgentChatRespVO chat(Long userId, String message, String imageDataUrl) {
        // 该端点会真实消耗模型额度，必须登录后使用，不接受匿名调用
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        // 未配置模型时明确报错，不返回编造的回复
        if (!modelClient.isReady()) {
            throw exception(AGENT_MODEL_NOT_CONFIGURED);
        }

        long startedAt = System.currentTimeMillis();
        // 先把职位清单查出来再调模型。早先这里只发用户那句话，模型手上一条职位都没有，
        // 只会如实回「我这边看不到任何在招职位」—— 一个找不到工作的求职助理等于没有。
        HrmRecruitAgentJobContextBuilder.JobContext context = jobContextBuilder.build(userId);
        HrmRecruitAgentModelResponse response = modelClient.chat(new HrmRecruitAgentModelRequest()
                .setScene(SCENE)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.AGENT_CHAT_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.AGENT_CHAT_SYSTEM)
                .setUserPayload(context.text() + "\n\n【用户发来的内容】\n" + message)
                // 模型是多模态的，图片直接给它看，不要在前端先 OCR 成文字 ——
                // 简历照片、截图被 OCR 之后丢掉的信息（版式、手写、印章）太多
                .setImageDataUrl(imageDataUrl)
                // 全局 json-mode 是为结构化抽取开的；自由对话要的是自然语言，这里必须关掉
                .setJsonOutput(false));
        // 只记耗时与用量，不记对话内容
        log.info("[hrm][agent] 求职助理对话完成：model={}, 耗时={}ms, totalTokens={}",
                response.getModel(), System.currentTimeMillis() - startedAt, response.getTotalTokens());

        // 模型用 [JOBS] 标记「这条回答在给对方列岗位」。标记是给程序看的，必须从回复里摘掉，
        // 否则用户会在句尾看见一个方括号单词。没标记就不挂卡片 —— 他问「简历怎么写」时
        // 底下弹十张岗位卡是噪音。
        String reply = StrUtil.emptyIfNull(response.getContent());
        boolean listing = reply.contains(JOB_MARKER);
        if (listing) {
            reply = reply.replace(JOB_MARKER, "").trim();
        }

        HrmPortalRecruitAgentChatRespVO respVO = new HrmPortalRecruitAgentChatRespVO();
        respVO.setReply(reply);
        // 清单为空时即使模型加了标记也没卡片可挂，一并按 false 处理，前端不用再判一次
        respVO.setListing(listing && !context.jobs().isEmpty());
        if (Boolean.TRUE.equals(respVO.getListing())) {
            respVO.setJobs(context.jobs());
        }
        respVO.setModel(response.getModel());
        respVO.setPromptVersion(HrmRecruitAgentPromptTemplates.AGENT_CHAT_VERSION);
        return respVO;
    }
}
