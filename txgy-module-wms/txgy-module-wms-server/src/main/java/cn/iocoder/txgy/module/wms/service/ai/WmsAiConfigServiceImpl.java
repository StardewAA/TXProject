package cn.iocoder.txgy.module.wms.service.ai;

import cn.iocoder.txgy.module.ai.api.model.AiChatModelApi;
import cn.iocoder.txgy.module.ai.api.model.AiChatModelConnDTO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiConfigSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.ai.WmsAiConfigDO;
import cn.iocoder.txgy.module.wms.dal.mysql.ai.WmsAiConfigMapper;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.AI_MODEL_CONFIG_INVALID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * WMS AI 客服配置 Service 实现
 */
@Service
@Slf4j
public class WmsAiConfigServiceImpl implements WmsAiConfigService {

    /** 环境变量兜底（与 WmsAiServiceImpl 原来的读取方式保持一致，便于平滑升级） */
    @Value("${txgy.ai.siliconflow.api-key:}")
    private String envApiKey;
    @Value("${txgy.ai.siliconflow.base-url:}")
    private String envBaseUrl;
    @Value("${txgy.ai.siliconflow.model:}")
    private String envModel;
    @Value("${txgy.ai.siliconflow.vision-model:}")
    private String envVisionModel;
    @Value("${txgy.ai.siliconflow.thinking-type:}")
    private String envThinkingType;
    @Value("${txgy.ai.siliconflow.timeout-seconds:240}")
    private int envTimeoutSeconds;

    @Resource
    private WmsAiConfigMapper configMapper;
    @Resource
    private AiChatModelApi aiChatModelApi;

    @Override
    public WmsAiConfigRespVO getConfig() {
        WmsAiConfigDO config = configMapper.selectLatest();
        WmsAiRuntimeConfig runtime = resolveRuntimeConfig();

        WmsAiConfigRespVO resp = new WmsAiConfigRespVO();
        resp.setEffectiveSource(runtime.getSource().name());
        resp.setEffectiveModel(runtime.getModel());
        resp.setEffectiveBaseUrl(runtime.getBaseUrl());
        if (config != null) {
            resp.setId(config.getId());
            resp.setEnabled(config.getEnabled());
            resp.setTimeoutSeconds(config.getTimeoutSeconds());
            resp.setRemark(config.getRemark());
            resp.setModelId(config.getModelId());
        }
        // 补充所选模型的展示信息，便于配置页确认「选的是哪个平台的哪个模型」
        if (config != null && config.getModelId() != null) {
            AiChatModelConnDTO conn = fetchConn(config.getModelId());
            if (conn != null) {
                resp.setModel(conn.getModel());
                resp.setModelName(StrUtil.blankToDefault(conn.getName(), conn.getModel()));
                resp.setPlatform(StrUtil.blankToDefault(conn.getPlatformName(), conn.getPlatform()));
            }
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(WmsAiConfigSaveReqVO reqVO) {
        // 先校验模型可用，避免把不可用的模型存进去、运行期才发现
        AiChatModelConnDTO conn = fetchConn(reqVO.getModelId());
        if (conn == null || StrUtil.isBlank(conn.getBaseUrl()) || StrUtil.isBlank(conn.getApiKey())) {
            throw exception(AI_MODEL_CONFIG_INVALID, 
                    "所选模型不可用：请确认该模型已关联 API 密钥，且密钥已填写 Key、平台地址可解析");
        }

        WmsAiConfigDO existing = configMapper.selectLatest();
        WmsAiConfigDO toSave = WmsAiConfigDO.builder()
                .modelId(reqVO.getModelId())
                .enabled(reqVO.getEnabled() == null ? Boolean.TRUE : reqVO.getEnabled())
                .timeoutSeconds(reqVO.getTimeoutSeconds())
                .remark(reqVO.getRemark())
                .build();
        if (existing == null) {
            configMapper.insert(toSave);
        } else {
            toSave.setId(existing.getId());
            configMapper.updateById(toSave);
        }
        log.info("[saveConfig][WMS AI 模型配置已更新 modelId({}) enabled({})]",
                reqVO.getModelId(), toSave.getEnabled());
    }

    @Override
    public WmsAiRuntimeConfig resolveRuntimeConfig() {
        WmsAiRuntimeConfig runtime = new WmsAiRuntimeConfig();

        // 1. 优先界面配置：enabled 且填了模型
        WmsAiConfigDO config = configMapper.selectLatest();
        if (config != null && Boolean.TRUE.equals(config.getEnabled()) && config.getModelId() != null) {
            AiChatModelConnDTO conn = fetchConn(config.getModelId());
            if (conn != null && StrUtil.isNotBlank(conn.getBaseUrl()) && StrUtil.isNotBlank(conn.getApiKey())) {
                runtime.setSource(WmsAiRuntimeConfig.Source.CONFIG);
                runtime.setBaseUrl(conn.getBaseUrl());
                runtime.setApiKey(conn.getApiKey());
                runtime.setModel(conn.getModel());
                // 自研 AI 客服的图片/文件识别复用同一个模型（该模型需支持视觉或多模态）
                runtime.setVisionModel(conn.getModel());
                runtime.setThinkingType(envThinkingType);
                runtime.setTimeoutSeconds(config.getTimeoutSeconds() == null
                        ? envTimeoutSeconds : config.getTimeoutSeconds());
                return runtime;
            }
            log.warn("[resolveRuntimeConfig][界面配置的模型不可用，回落环境变量 modelId({})]",
                    config.getModelId());
        }

        // 2. 回落环境变量
        runtime.setSource(StrUtil.isBlank(envApiKey) ? WmsAiRuntimeConfig.Source.NONE
                : WmsAiRuntimeConfig.Source.ENV);
        runtime.setBaseUrl(envBaseUrl);
        runtime.setApiKey(envApiKey);
        runtime.setModel(envModel);
        runtime.setVisionModel(StrUtil.blankToDefault(envVisionModel, envModel));
        runtime.setThinkingType(envThinkingType);
        runtime.setTimeoutSeconds(envTimeoutSeconds);
        return runtime;
    }

    /** 调 AI 模块取连接信息；失败返回 null（不抛异常，避免配置页打不开） */
    private AiChatModelConnDTO fetchConn(Long modelId) {
        if (modelId == null) {
            return null;
        }
        try {
            return aiChatModelApi.getChatModelConn(modelId).getCheckedData();
        } catch (Exception e) {
            log.warn("[fetchConn][获取模型连接信息失败 modelId({}) err({})]", modelId, e.getMessage());
            return null;
        }
    }

    @Override
    public long testModel(Long modelId) {
        // 1. 取要测试的连接信息：指定了 modelId 就测它，否则测当前生效配置
        String baseUrl;
        String apiKey;
        String model;
        if (modelId != null) {
            AiChatModelConnDTO conn = fetchConn(modelId);
            if (conn == null) {
                throw exception(AI_MODEL_CONFIG_INVALID, "模型不存在或读取连接信息失败，请确认该模型已关联 API 密钥");
            }
            if (StrUtil.isBlank(conn.getBaseUrl())) {
                throw exception(AI_MODEL_CONFIG_INVALID, "该模型无法解析调用地址：请检查关联密钥的「自定义 API URL」或平台是否被支持");
            }
            if (StrUtil.isBlank(conn.getApiKey())) {
                throw exception(AI_MODEL_CONFIG_INVALID, "该模型关联的 API 密钥为空，请先到「API 密钥」页补全");
            }
            if (StrUtil.isBlank(conn.getModel())) {
                throw exception(AI_MODEL_CONFIG_INVALID, "该模型未填写模型标识（如 deepseek-chat），请到「模型配置」页补全");
            }
            baseUrl = conn.getBaseUrl();
            apiKey = conn.getApiKey();
            model = conn.getModel();
        } else {
            WmsAiRuntimeConfig runtime = resolveRuntimeConfig();
            if (!runtime.usable()) {
                throw exception(AI_MODEL_CONFIG_INVALID, "当前没有可用的模型配置，请先选择模型或配置环境变量");
            }
            baseUrl = runtime.getBaseUrl();
            apiKey = runtime.getApiKey();
            model = runtime.getModel();
        }

        // 2. 发一句最短对话请求，验证「地址 + Key + 模型名」三者可用，
        //    让配置错误在保存时就暴露，而不是等用户聊天才发现
        long start = System.currentTimeMillis();
        try {
            JSONObject body = new JSONObject(true);
            body.set("model", model);
            body.set("max_tokens", 8);
            body.set("temperature", 0.0);
            JSONArray messages = new JSONArray();
            JSONObject user = new JSONObject(true);
            user.set("role", "user");
            user.set("content", "hi");
            messages.add(user);
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(),
                            java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            if (response.statusCode() != 200) {
                throw exception(AI_MODEL_CONFIG_INVALID, "调用失败（HTTP " + response.statusCode() + "）："
                        + StrUtil.maxLength(StrUtil.nullToEmpty(response.body()), 300));
            }
            JSONObject root = JSONUtil.parseObj(response.body());
            if (root.getJSONArray("choices") == null || root.getJSONArray("choices").isEmpty()) {
                throw exception(AI_MODEL_CONFIG_INVALID, "调用返回异常（未包含 choices）："
                        + StrUtil.maxLength(response.body(), 300));
            }
            log.info("[testModel][连通性测试通过 model({}) elapsed({}ms)]", model, elapsed);
            return elapsed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[testModel][连通性测试失败 model({}) elapsed({}ms) err({})]", model, elapsed, e.getMessage());
            throw exception(AI_MODEL_CONFIG_INVALID, "调用失败：" + e.getMessage()
                    + "（地址 " + baseUrl + "，模型 " + model + "）");
        }
    }

}
