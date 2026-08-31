package cn.iocoder.txgy.module.ai.framework.ai.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.AiModelFactory;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.AiModelFactoryImpl;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.baichuan.BaiChuanChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.doubao.DouBaoChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.grok.GrokChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.hunyuan.HunYuanChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.midjourney.api.MidjourneyApi;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.minimax.MiniMaxChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.moonshot.MoonshotChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.siliconflow.SiliconFlowApiConstants;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.siliconflow.SiliconFlowChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.stepfun.StepFunChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.suno.api.SunoApi;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.xinghuo.XingHuoChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.yiyan.YiYanChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.model.zhipu.ZhiPuChatModel;
import cn.iocoder.txgy.module.ai.framework.ai.core.webserch.AiWebSearchClient;
import cn.iocoder.txgy.module.ai.framework.ai.core.webserch.bocha.AiBoChaWebSearchClient;
import cn.iocoder.txgy.module.ai.tool.method.PersonService;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OPENLAB BS AI 自动配置
 *
 * @author fansili
 */
@Configuration
@EnableConfigurationProperties({ TxgyAiProperties.class,
        QdrantVectorStoreProperties.class, // 解析 Qdrant 配置
        RedisVectorStoreProperties.class, // 解析 Redis 配置
        MilvusVectorStoreProperties.class, MilvusServiceClientProperties.class // 解析 Milvus 配置
})
@Slf4j
public class AiAutoConfiguration {

    @Bean
    public AiModelFactory aiModelFactory() {
        return new AiModelFactoryImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        // 特殊：兜底有 ObservationRegistry Bean，避免相关的 ChatModel 创建报错。相关 issue：https://t.zsxq.com/CuPu4
        return ObservationRegistry.NOOP;
    }

    // ========== 各种 AI Client 创建 ==========

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "spring.ai.dashscope.api-key")
    public DashScopeChatModel dashScopeChatModel(@Value("${spring.ai.dashscope.api-key}") String apiKey,
                                                 ToolCallingManager toolCallingManager,
                                                 ObservationRegistry observationRegistry) {
        return buildTongYiChatModel(apiKey, toolCallingManager, observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "spring.ai.dashscope.api-key")
    public DashScopeImageModel dashScopeImageModel(@Value("${spring.ai.dashscope.api-key}") String apiKey,
                                                   ObservationRegistry observationRegistry) {
        return buildTongYiImagesModel(apiKey, observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "spring.ai.dashscope.api-key")
    public DashScopeEmbeddingModel dashScopeEmbeddingModel(@Value("${spring.ai.dashscope.api-key}") String apiKey,
                                                           ObservationRegistry observationRegistry) {
        return buildTongYiEmbeddingModel(apiKey, null, observationRegistry);
    }

    public static DashScopeChatModel buildTongYiChatModel(String apiKey) {
        return buildTongYiChatModel(apiKey, getToolCallingManager(), getObservationRegistry());
    }

    private static DashScopeChatModel buildTongYiChatModel(String apiKey, ToolCallingManager toolCallingManager,
                                                           ObservationRegistry observationRegistry) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(DashScopeApi.DEFAULT_CHAT_MODEL)
                .temperature(0.7)
                .build();
        return new DashScopeChatModel(dashScopeApi, options, toolCallingManager, RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry);
    }

    public static DashScopeImageModel buildTongYiImagesModel(String apiKey) {
        return buildTongYiImagesModel(apiKey, getObservationRegistry());
    }

    private static DashScopeImageModel buildTongYiImagesModel(String apiKey, ObservationRegistry observationRegistry) {
        DashScopeImageApi dashScopeImageApi = DashScopeImageApi.builder().apiKey(apiKey).build();
        DashScopeImageOptions options = DashScopeImageOptions.builder()
                .model(DashScopeImageApi.DEFAULT_IMAGE_MODEL)
                .build();
        return new DashScopeImageModel(dashScopeImageApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry);
    }

    public static DashScopeEmbeddingModel buildTongYiEmbeddingModel(String apiKey, String model) {
        return buildTongYiEmbeddingModel(apiKey, model, getObservationRegistry());
    }

    private static DashScopeEmbeddingModel buildTongYiEmbeddingModel(String apiKey, String model,
                                                                     ObservationRegistry observationRegistry) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(StrUtil.blankToDefault(model, DashScopeApi.DEFAULT_EMBEDDING_MODEL))
                .build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.doubao.enable", havingValue = "true")
    public DouBaoChatModel douBaoChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.DouBao properties = txgyAiProperties.getDoubao();
        return buildDouBaoChatClient(properties);
    }

    public DouBaoChatModel buildDouBaoChatClient(TxgyAiProperties.DouBao properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(DouBaoChatModel.MODEL_DEFAULT);
        }
        DeepSeekChatModel openAiChatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl(DouBaoChatModel.BASE_URL)
                        .completionsPath(DouBaoChatModel.COMPLETE_PATH)
                        .apiKey(properties.getApiKey())
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new DouBaoChatModel(openAiChatModel);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.siliconflow.enable", havingValue = "true")
    public SiliconFlowChatModel siliconFlowChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.SiliconFlow properties = txgyAiProperties.getSiliconflow();
        return buildSiliconFlowChatClient(properties);
    }

    public SiliconFlowChatModel buildSiliconFlowChatClient(TxgyAiProperties.SiliconFlow properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(SiliconFlowApiConstants.MODEL_DEFAULT);
        }
        DeepSeekChatModel openAiChatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl(SiliconFlowApiConstants.DEFAULT_BASE_URL)
                        .apiKey(properties.getApiKey())
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new SiliconFlowChatModel(openAiChatModel);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.hunyuan.enable", havingValue = "true")
    public HunYuanChatModel hunYuanChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.HunYuan properties = txgyAiProperties.getHunyuan();
        return buildHunYuanChatClient(properties);
    }

    public HunYuanChatModel buildHunYuanChatClient(TxgyAiProperties.HunYuan properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(HunYuanChatModel.MODEL_DEFAULT);
        }
        if (StrUtil.isEmpty(properties.getBaseUrl())) {
            properties.setBaseUrl(HunYuanChatModel.BASE_URL);
        }
        // 创建 DeepSeekChatModel、HunYuanChatModel 对象
        DeepSeekChatModel openAiChatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl(properties.getBaseUrl())
                        .completionsPath(HunYuanChatModel.COMPLETE_PATH)
                        .apiKey(properties.getApiKey())
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new HunYuanChatModel(openAiChatModel);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.xinghuo.enable", havingValue = "true")
    public XingHuoChatModel xingHuoChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.XingHuo properties = txgyAiProperties.getXinghuo();
        return buildXingHuoChatClient(properties);
    }

    public XingHuoChatModel buildXingHuoChatClient(TxgyAiProperties.XingHuo properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(XingHuoChatModel.MODEL_DEFAULT);
        }
        return XingHuoChatModel.builder()
                .apiKey(properties.getApiKey())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.baichuan.enable", havingValue = "true")
    public BaiChuanChatModel baiChuanChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.BaiChuan properties = txgyAiProperties.getBaichuan();
        return buildBaiChuanChatClient(properties);
    }

    public BaiChuanChatModel buildBaiChuanChatClient(TxgyAiProperties.BaiChuan properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(BaiChuanChatModel.MODEL_DEFAULT);
        }
        DeepSeekChatModel deepSeekChatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl(BaiChuanChatModel.BASE_URL)
                        .apiKey(properties.getApiKey())
                        .completionsPath(BaiChuanChatModel.COMPLETE_PATH)
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new BaiChuanChatModel(deepSeekChatModel);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.yiyan.enable", havingValue = "true")
    public YiYanChatModel yiYanChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.YiYan properties = txgyAiProperties.getYiyan();
        return buildYiYanChatClient(properties);
    }

    public YiYanChatModel buildYiYanChatClient(TxgyAiProperties.YiYan properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(YiYanChatModel.MODEL_DEFAULT);
        }
        return new YiYanChatModel(buildDeepSeekCompatibleChatModel(
                StrUtil.blankToDefault(properties.getBaseUrl(), YiYanChatModel.BASE_URL),
                null, properties.getApiKey(), properties.getModel(), properties.getTemperature(),
                properties.getMaxTokens(), properties.getTopP()));
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.zhipu.enable", havingValue = "true")
    public ZhiPuChatModel zhiPuChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.ZhiPu properties = txgyAiProperties.getZhipu();
        return buildZhiPuChatClient(properties);
    }

    public ZhiPuChatModel buildZhiPuChatClient(TxgyAiProperties.ZhiPu properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(ZhiPuChatModel.MODEL_DEFAULT);
        }
        DeepSeekChatModel deepSeekChatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl(StrUtil.blankToDefault(properties.getBaseUrl(), ZhiPuChatModel.BASE_URL))
                        .apiKey(properties.getApiKey())
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new ZhiPuChatModel(deepSeekChatModel);
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.minimax.enable", havingValue = "true")
    public MiniMaxChatModel miniMaxChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.MiniMax properties = txgyAiProperties.getMinimax();
        return buildMiniMaxChatClient(properties);
    }

    public MiniMaxChatModel buildMiniMaxChatClient(TxgyAiProperties.MiniMax properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(MiniMaxChatModel.MODEL_DEFAULT);
        }
        return new MiniMaxChatModel(buildDeepSeekCompatibleChatModel(
                StrUtil.blankToDefault(properties.getBaseUrl(), MiniMaxChatModel.BASE_URL),
                null, properties.getApiKey(), properties.getModel(), properties.getTemperature(),
                properties.getMaxTokens(), properties.getTopP()));
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.moonshot.enable", havingValue = "true")
    public MoonshotChatModel moonshotChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.Moonshot properties = txgyAiProperties.getMoonshot();
        return buildMoonshotChatClient(properties);
    }

    public MoonshotChatModel buildMoonshotChatClient(TxgyAiProperties.Moonshot properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(MoonshotChatModel.MODEL_DEFAULT);
        }
        return new MoonshotChatModel(buildDeepSeekCompatibleChatModel(
                StrUtil.blankToDefault(properties.getBaseUrl(), MoonshotChatModel.BASE_URL),
                MoonshotChatModel.COMPLETE_PATH, properties.getApiKey(), properties.getModel(),
                properties.getTemperature(), properties.getMaxTokens(), properties.getTopP()));
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.stepfun.enable", havingValue = "true")
    public StepFunChatModel stepFunChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.StepFun properties = txgyAiProperties.getStepfun();
        return buildStepFunChatClient(properties);
    }

    public StepFunChatModel buildStepFunChatClient(TxgyAiProperties.StepFun properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(StepFunChatModel.MODEL_DEFAULT);
        }
        return new StepFunChatModel(buildDeepSeekCompatibleChatModel(
                StrUtil.blankToDefault(properties.getBaseUrl(), StepFunChatModel.BASE_URL),
                StepFunChatModel.COMPLETE_PATH, properties.getApiKey(), properties.getModel(),
                properties.getTemperature(), properties.getMaxTokens(), properties.getTopP()));
    }

    private static DeepSeekChatModel buildDeepSeekCompatibleChatModel(String baseUrl, String completionsPath,
                                                                     String apiKey, String model,
                                                                     Double temperature, Integer maxTokens,
                                                                     Double topP) {
        DeepSeekApi.Builder apiBuilder = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey);
        if (StrUtil.isNotEmpty(completionsPath)) {
            apiBuilder.completionsPath(completionsPath);
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(apiBuilder.build())
                .options(DeepSeekChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .topP(topP)
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.midjourney.enable", havingValue = "true")
    public MidjourneyApi midjourneyApi(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.Midjourney config = txgyAiProperties.getMidjourney();
        return new MidjourneyApi(config.getBaseUrl(), config.getApiKey(), config.getNotifyUrl());
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.suno.enable", havingValue = "true")
    public SunoApi sunoApi(TxgyAiProperties txgyAiProperties) {
        return new SunoApi(txgyAiProperties.getSuno().getBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.grok.enable", havingValue = "true")
    public GrokChatModel grokChatClient(TxgyAiProperties txgyAiProperties) {
        TxgyAiProperties.Grok properties = txgyAiProperties.getGrok();
        return buildGrokChatClient(properties);
    }

    public GrokChatModel buildGrokChatClient(TxgyAiProperties.Grok properties) {
        if (StrUtil.isEmpty(properties.getModel())) {
            properties.setModel(GrokChatModel.MODEL_DEFAULT);
        }
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(StrUtil.blankToDefault(properties.getBaseUrl(), GrokChatModel.BASE_URL))
                        .apiKey(properties.getApiKey())
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .topP(properties.getTopP())
                        .build())
                .build();
        return new GrokChatModel(openAiChatModel);
    }

    // ========== RAG 相关 ==========

    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }

    @Bean
    public BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    private static ToolCallingManager getToolCallingManager() {
        return SpringUtil.getBean(ToolCallingManager.class);
    }

    private static ObservationRegistry getObservationRegistry() {
        return SpringUtil.getBean(ObservationRegistry.class);
    }

    // ========== Web Search 相关 ==========

    @Bean
    @ConditionalOnProperty(value = "txgy.ai.web-search.enable", havingValue = "true")
    public AiWebSearchClient webSearchClient(TxgyAiProperties txgyAiProperties) {
        return new AiBoChaWebSearchClient(txgyAiProperties.getWebSearch().getApiKey());
    }

    // ========== MCP 相关 ==========

    /**
     * 参考自 <a href="https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html">MCP Server Boot Starter</>
     */
    @Bean
    public List<ToolCallback> toolCallbacks(PersonService personService) {
        return List.of(ToolCallbacks.from(personService));
    }

}
