package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.ai.api.model.AiRuntimeModelApi;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_RAG_UNAVAILABLE;

/**
 * 门户 AI 助手的大模型直答能力：不依赖知识库召回，走 AI 模块的可切换模型，
 * 与「知识库问答（带引用）」互为补充。
 */
@Service
@Slf4j
public class CmsAiChatService {

    private static final String GENERAL_SYSTEM_PROMPT = """
            你是"小天"，天信之星防腐保温行业知识服务平台的 AI 助手。
            回答要求：
            1. 使用简体中文，条理清晰、语气亲切，控制在 500 字以内；
            2. 涉及法规条款号、标准编号、政策补贴金额、申报条件时，必须提醒用户"以下为通用大模型回答，请以知识库原文与官方文件为准"；
            3. 绝对禁止编造不存在的标准号、条款号、文号或具体金额；不确定时明确说明不确定。
            """;

    private final ObjectProvider<AiRuntimeModelApi> modelApiProvider;
    private final JdbcTemplate jdbc;

    public CmsAiChatService(ObjectProvider<AiRuntimeModelApi> modelApiProvider, JdbcTemplate jdbc) {
        this.modelApiProvider = modelApiProvider;
        this.jdbc = jdbc;
    }

    /** 门户可选的大模型列表，来自 AI 模块已启用且排序靠前的对话模型。 */
    public List<ModelOption> listChatModels() {
        return jdbc.query("""
                SELECT id, name, model, platform FROM ai_model
                WHERE deleted=0 AND type=1 AND status=0 AND tenant_id=?
                ORDER BY sort ASC, id ASC
                """,
                (rs, row) -> new ModelOption(rs.getLong("id"), rs.getString("name"),
                        rs.getString("model"), rs.getString("platform")),
                TenantContextHolder.getRequiredTenantId());
    }

    /** 大模型直答：拼接最近几轮对话，不注入知识库片段。 */
    public ChatResult chat(String question, List<History> history, Long modelId) {
        AiRuntimeModelApi modelApi = modelApiProvider.getIfAvailable();
        if (modelApi == null) {
            throw exception(LAW_RAG_UNAVAILABLE);
        }
        AiRuntimeChatReqDTO request = new AiRuntimeChatReqDTO();
        request.setModelId(modelId);
        request.setSystemPrompt(GENERAL_SYSTEM_PROMPT);
        request.setUserPrompt(buildPrompt(question, history));
        request.setTemperature(0.4d);
        request.setMaxTokens(1200);
        try {
            AiRuntimeChatRespDTO response = modelApi.chat(request).getCheckedData();
            String model = response.getPlatform() + ":" + response.getModel();
            return new ChatResult(response.getContent(), model, response.getModelName());
        } catch (RuntimeException ex) {
            log.warn("[chat][大模型直答失败 modelId={}]", modelId, ex);
            throw exception(LAW_RAG_UNAVAILABLE);
        }
    }

    private static String buildPrompt(String question, List<History> history) {
        StringBuilder prompt = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            prompt.append("以下是最近的对话记录：").append(System.lineSeparator());
            for (History item : history) {
                prompt.append("用户：").append(item.question()).append(System.lineSeparator());
                prompt.append("助手：").append(item.answer()).append(System.lineSeparator());
            }
            prompt.append(System.lineSeparator());
        }
        prompt.append("当前问题：").append(question);
        return prompt.toString();
    }

    public record ModelOption(Long id, String name, String model, String platform) { }

    public record History(String question, String answer) { }

    public record ChatResult(String answer, String model, String modelName) { }
}