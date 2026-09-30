package cn.iocoder.txgy.module.ai.api.model;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatRespDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeEmbeddingReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeEmbeddingRespDTO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiModelDO;
import cn.iocoder.txgy.module.ai.enums.model.AiModelTypeEnum;
import cn.iocoder.txgy.module.ai.enums.model.AiPlatformEnum;
import cn.iocoder.txgy.module.ai.service.model.AiModelService;
import cn.iocoder.txgy.module.ai.service.model.AiModelSceneRouteService;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** AI 模型运行时 RPC。模型选择实时读取 ai_model，不在调用方写死。 */
@RestController
@Validated
@Slf4j
public class AiRuntimeModelApiImpl implements AiRuntimeModelApi {

    @Resource
    private AiModelService modelService;
    @Resource
    private AiModelSceneRouteService sceneRouteService;

    @Override
    public CommonResult<AiRuntimeChatRespDTO> chat(AiRuntimeChatReqDTO request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        messages.add(new UserMessage(request.getUserPrompt()));
        RuntimeException last = null;
        for (AiModelDO model : resolveModels(request.getModelId(), request.getSceneCode(), AiModelTypeEnum.CHAT.getType())) {
            try {
                ChatOptions options = AiUtils.buildChatOptions(
                        AiPlatformEnum.validatePlatform(model.getPlatform()), model.getModel(),
                        request.getTemperature() == null ? model.getTemperature() : request.getTemperature(),
                        request.getMaxTokens() == null ? model.getMaxTokens() : request.getMaxTokens());
                ChatResponse response = modelService.getChatModel(model.getId()).call(new Prompt(messages, options));
                return success(new AiRuntimeChatRespDTO(AiUtils.getChatResponseContent(response), model.getId(),
                        model.getName(), model.getModel(), model.getPlatform()));
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("[chat][模型调用失败，尝试备用模型 sceneCode={} modelId={}]",
                        request.getSceneCode(), model.getId(), ex);
            }
        }
        throw last == null ? new IllegalStateException("没有可用的对话模型") : last;
    }

    @Override
    public CommonResult<AiRuntimeEmbeddingRespDTO> embedding(AiRuntimeEmbeddingReqDTO request) {
        RuntimeException last = null;
        for (AiModelDO model : resolveModels(request.getModelId(), request.getSceneCode(), AiModelTypeEnum.EMBEDDING.getType())) {
            try {
                EmbeddingModel embeddingModel = modelService.getEmbeddingModel(model.getId());
                List<List<Float>> vectors = request.getTexts().stream()
                        .map(embeddingModel::embed)
                        .map(AiRuntimeModelApiImpl::boxed)
                        .toList();
                int dimensions = vectors.isEmpty() ? 0 : vectors.getFirst().size();
                return success(new AiRuntimeEmbeddingRespDTO(vectors, dimensions, model.getId(), model.getName(),
                        model.getModel(), model.getPlatform()));
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("[embedding][模型调用失败，尝试备用模型 sceneCode={} modelId={}]",
                        request.getSceneCode(), model.getId(), ex);
            }
        }
        throw last == null ? new IllegalStateException("没有可用的向量模型") : last;
    }

    private List<AiModelDO> resolveModels(Long explicitId, String sceneCode, Integer type) {
        if (explicitId != null) return List.of(resolveModel(explicitId, type));
        List<Long> routeIds = sceneRouteService.resolve(sceneCode);
        if (!routeIds.isEmpty()) {
            List<AiModelDO> result = new ArrayList<>(routeIds.size());
            for (Long id : routeIds) result.add(resolveModel(id, type));
            return List.copyOf(result);
        }
        return List.of(resolveModel(null, type));
    }

    private AiModelDO resolveModel(Long id, Integer type) {
        if (id != null) {
            AiModelDO model = modelService.validateModel(id);
            if (!type.equals(model.getType())) {
                throw new IllegalArgumentException("所选模型类型与调用场景不匹配");
            }
            return model;
        }
        // 每次调用都按状态和排序重新选择，后台切换默认模型后立即生效。
        List<AiModelDO> enabled = modelService.getModelListByStatusAndType(
                CommonStatusEnum.ENABLE.getStatus(), type, null);
        if (enabled.isEmpty()) {
            return modelService.getRequiredDefaultModel(type);
        }
        return enabled.getFirst();
    }

    private static List<Float> boxed(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) result.add(value);
        return List.copyOf(result);
    }
}
