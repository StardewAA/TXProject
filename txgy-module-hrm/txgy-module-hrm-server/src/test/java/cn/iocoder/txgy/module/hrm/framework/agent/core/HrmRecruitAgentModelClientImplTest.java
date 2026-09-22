package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.hrm.framework.agent.config.HrmRecruitAgentModelProperties;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_NOT_CONFIGURED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型适配层测试。
 *
 * <p>核心约束：未配置模型时必须明确报错，且不得发起任何网络请求、不得返回编造内容。
 * 这里不做真实模型调用，真实联调另行受控进行。</p>
 */
public class HrmRecruitAgentModelClientImplTest {

    private HrmRecruitAgentModelClientImpl buildClient(boolean enabled, String baseUrl,
                                                       String apiKey, String model) {
        HrmRecruitAgentModelProperties properties = new HrmRecruitAgentModelProperties();
        properties.setEnabled(enabled);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setModel(model);
        return new HrmRecruitAgentModelClientImpl(properties);
    }

    @Test
    public void testIsReady_requiresEveryField() {
        assertFalse(buildClient(false, "https://example.com/v1", "key", "qwen-flash").isReady());
        assertFalse(buildClient(true, null, "key", "qwen-flash").isReady());
        assertFalse(buildClient(true, "https://example.com/v1", "  ", "qwen-flash").isReady());
        assertFalse(buildClient(true, "https://example.com/v1", "key", null).isReady());
        assertTrue(buildClient(true, "https://example.com/v1", "key", "qwen-flash").isReady());
    }

    @Test
    public void testChat_throwsWhenNotConfiguredAndSendsNothing() {
        // 未启用：即便填了地址与密钥也不应发起调用
        HrmRecruitAgentModelClientImpl client =
                buildClient(false, "https://invalid.example.com/v1", "key", "qwen-flash");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> client.chat(new HrmRecruitAgentModelRequest()
                        .setScene("RESUME_ANALYZE")
                        .setPromptVersion("v1")
                        .setSystemPrompt("system")
                        .setUserPayload("{}")));
        assertEquals(AGENT_MODEL_NOT_CONFIGURED.getCode(), exception.getCode());
    }

    @Test
    public void testChat_throwsWhenApiKeyMissing() {
        HrmRecruitAgentModelClientImpl client =
                buildClient(true, "https://invalid.example.com/v1", null, "qwen-flash");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> client.chat(new HrmRecruitAgentModelRequest()));
        assertEquals(AGENT_MODEL_NOT_CONFIGURED.getCode(), exception.getCode());
    }

    @Test
    public void testModelName_exposesConfiguredModelForAudit() {
        assertEquals("deepseek-chat",
                buildClient(true, "https://api.deepseek.com/v1", "key", "deepseek-chat").modelName());
    }
}
