package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 专员改完提示词/技能后能立刻试跑（30 秒验证）
// [为什么] 2026-09-16 口径：智能体管理自持云端模型，试跑不再走平台 AI 模块（bidding.ai.*）；
//           技能片段与后台提示词一并注入 system prompt，试跑所见即生成所得

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentPreviewVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentRetrieveVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;
import cn.iocoder.txgy.module.bidding.framework.bidagent.LlmConfig;
import cn.iocoder.txgy.module.bidding.framework.bidagent.OpenAiCompatibleChatClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_PREVIEW_UNAVAILABLE;

@Slf4j
@Service
public class BidAgentPreviewServiceImpl implements BidAgentPreviewService {

    private static final String PROMPT_SOURCE_BID_AGENT = "BID_AGENT";
    private static final String PROMPT_SOURCE_BUILT_IN = "BUILT_IN";

    @Resource
    private BidAgentProfileService profileService;
    @Resource
    private BidAgentRetrieveService retrieveService;
    @Resource
    private BidAgentModelService modelService;
    @Resource
    private BidAgentSkillService skillService;
    @Resource
    private OpenAiCompatibleChatClient chatClient;

    @Override
    public BidAgentPreviewVO.RespVO preview(BidAgentPreviewVO.ReqVO reqVO) {
        LlmConfig config;
        try {
            config = modelService.resolveEnabledConfig();
        } catch (Exception ex) {
            log.warn("[BidAgentPreviewServiceImpl][preview] 云端模型未就绪：{}", ex.getMessage());
            throw exception(BID_AGENT_PREVIEW_UNAVAILABLE, StrUtil.maxLength(ex.getMessage(), 300));
        }
        BidAgentProfileVO.RespVO profile = profileService.getByTaskType(reqVO.getTaskType());
        // 提示词以后台为唯一事实源（2026-09-28 起五类均可编辑）；后台行有提示词即用，否则回退内置基线
        boolean fromProfile = profile != null && StrUtil.isNotBlank(profile.getSystemPrompt());
        // 智能体可覆盖模型名 / 温度 / max-tokens
        if (profile != null && StrUtil.isNotBlank(profile.getModel())) {
            config.setModel(profile.getModel());
        }
        if (profile != null && profile.getTemperature() != null) {
            config.setTemperature(profile.getTemperature());
        }
        if (profile != null && profile.getMaxTokens() != null) {
            config.setMaxTokens(profile.getMaxTokens());
        }

        StringBuilder systemPrompt = new StringBuilder(
                fromProfile ? profile.getSystemPrompt() : builtInPrompt(reqVO.getTaskType()));
        if (profile != null && profile.getId() != null) {
            List<BidAgentSkillDO> skills = skillService.listByProfile(profile.getId(), reqVO.getTaskType(), false);
            for (BidAgentSkillDO skill : skills) {
                if (StrUtil.isNotBlank(skill.getPromptFragment())) {
                    systemPrompt.append("\n\n【技能：").append(skill.getName()).append("】\n")
                            .append(skill.getPromptFragment());
                }
            }
        }

        StringBuilder user = new StringBuilder();
        user.append("【待处理内容】\n").append(StrUtil.nullToEmpty(reqVO.getInput()));
        if (Boolean.TRUE.equals(reqVO.getWithRetrieve())) {
            try {
                BidAgentRetrieveVO.ReqVO retrieveReq = new BidAgentRetrieveVO.ReqVO();
                String input = StrUtil.nullToEmpty(reqVO.getInput());
                retrieveReq.setQuery(input.length() > 200 ? input.substring(0, 200) : input);
                retrieveReq.setTopK(3);
                BidAgentRetrieveVO.RespVO retrieved = retrieveService.retrieve(retrieveReq);
                if (retrieved.getChunks() != null && !retrieved.getChunks().isEmpty()) {
                    user.append("\n\n【参考资料（仅可引用，不得编造）】\n");
                    int index = 0;
                    for (BidAgentRetrieveVO.Chunk chunk : retrieved.getChunks()) {
                        index++;
                        user.append("[").append(index).append("] ").append(chunk.getContent()).append('\n');
                    }
                }
            } catch (Exception ex) {
                // 试跑允许"无资料"：但要显式告知，避免专员误以为带检索
                user.append("\n\n【参考资料】检索不可用（").append(ex.getMessage()).append("），本段试跑不带知识库上下文。");
            }
        }

        try {
            String output = chatClient.chat(config, systemPrompt.toString(), user.toString());
            BidAgentPreviewVO.RespVO resp = new BidAgentPreviewVO.RespVO();
            resp.setTaskType(reqVO.getTaskType());
            resp.setPromptSource(fromProfile ? PROMPT_SOURCE_BID_AGENT : PROMPT_SOURCE_BUILT_IN);
            resp.setOutput(output == null ? "" : output);
            return resp;
        } catch (Exception ex) {
            log.warn("[BidAgentPreviewServiceImpl][preview] 试跑失败 taskType={} msg={}", reqVO.getTaskType(), ex.getMessage());
            throw exception(BID_AGENT_PREVIEW_UNAVAILABLE, StrUtil.maxLength(ex.getMessage(), 300));
        }
    }

    /** 内置基线提示词（与 bidding_bid_agent 预置行保持一致；后台未配置时兜底） */
    private String builtInPrompt(String taskType) {
        List<String> prompts = List.of(
                "你是招投标业务专家。只依据给定材料文本抽取，不允许推断或补全；抽不到的字段留空。输出必须是严格 JSON。",
                "你是标书编制专家。章节树必须逐条承接招标文件的评分点与废标条款，每条都要标明承接来源。",
                "你是标书撰写专家。只写有证据支撑的内容；每个数字都必须能找到来源，找不到就写\"待补充\"。",
                "你只处理必须组织语言的字段；证书编号、金额、日期等事实字段一律不由你输出。",
                "你是投标文件合规审查专家。逐条判断投标文件是否满足招标文件的强制要求，输出 PASS/DOUBT/FAIL/MISS 与证据原文。");
        int index = List.of("PARSE", "OUTLINE", "DRAFT", "FILL", "REVIEW").indexOf(taskType);
        return prompts.get(index < 0 ? 0 : index);
    }
}
