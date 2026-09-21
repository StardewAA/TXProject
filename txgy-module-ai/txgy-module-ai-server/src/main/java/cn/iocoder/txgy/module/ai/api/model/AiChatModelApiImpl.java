package cn.iocoder.txgy.module.ai.api.model;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.model.AiModelDO;
import cn.iocoder.txgy.module.ai.enums.model.AiPlatformEnum;
import cn.iocoder.txgy.module.ai.service.model.AiApiKeyService;
import cn.iocoder.txgy.module.ai.service.model.AiModelService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.error;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * AI 聊天模型 API 实现
 *
 * <p>方法上加 {@link TenantIgnore}：模型与 API 密钥属于平台级资源（当前库中全部为
 * tenant_id=1），而调用方是各业务租户（如租户 2005 的 WMS）。若不忽略租户过滤，
 * 多租户插件会自动追加 tenant_id = 当前租户，导致「模型存在但查不到」。
 *
 * <p>注意：{@code TenantIgnoreAspect} 的切点是 {@code @annotation(...)}，只对
 * <b>方法级</b>注解生效，写在类上不会触发，因此这里必须加在方法上。
 */
@RestController
public class AiChatModelApiImpl implements AiChatModelApi {

    @Resource
    private AiModelService modelService;
    @Resource
    private AiApiKeyService apiKeyService;

    @Override
    @TenantIgnore
    public CommonResult<AiChatModelConnDTO> getChatModelConn(Long modelId) {
        if (modelId == null) {
            return error(400, "模型编号不能为空");
        }
        AiModelDO model = modelService.getModel(modelId);
        if (model == null) {
            return error(404, "模型不存在：" + modelId);
        }
        if (model.getKeyId() == null) {
            return error(400, "该模型未关联 API 密钥，无法调用");
        }
        AiApiKeyDO apiKey = apiKeyService.getApiKey(model.getKeyId());
        if (apiKey == null || StrUtil.isBlank(apiKey.getApiKey())) {
            return error(400, "该模型关联的 API 密钥未配置");
        }
        AiPlatformEnum platform = findPlatform(model.getPlatform());
        String baseUrl = resolveChatUrl(platform, apiKey.getUrl());

        AiChatModelConnDTO dto = new AiChatModelConnDTO();
        dto.setPlatform(model.getPlatform());
        dto.setPlatformName(platform == null ? null : platform.getName());
        dto.setBaseUrl(baseUrl);
        dto.setApiKey(apiKey.getApiKey());
        dto.setModel(model.getModel());
        dto.setName(model.getName());
        return success(dto);
    }

    /**
     * 解析出 OpenAI 兼容的 chat/completions 完整地址。
     *
     * <p>规则：Key 记录里配了 url 的，按「自定义网关」处理 ——
     * <ul>
     *   <li>已含 {@code /chat/completions}：原样使用</li>
     *   <li>已含版本段（{@code /v1}、{@code /api/v3}、{@code /v4} 等）：其后只补 {@code /chat/completions}
     *       （火山方舟的 {@code /api/v3} 就属于这种，补 {@code /v1} 会变成 404）</li>
     *   <li>只填了域名：补 {@code /v1/chat/completions}</li>
     * </ul>
     * 没配 url 的按平台官方地址补全。
     */
    private String resolveChatUrl(AiPlatformEnum platform, String keyUrl) {
        if (StrUtil.isNotBlank(keyUrl)) {
            String url = StrUtil.removeSuffix(keyUrl.trim(), "/");
            if (url.contains("/chat/completions")) {
                return url;
            }
            // 已带版本段的（/v1、/api/v3、/v4、/v1beta 等），只补叶子路径
            if (url.matches(".*/(v\\d+[a-z]*|api/v\\d+[a-z]*)$")) {
                return url + "/chat/completions";
            }
            // 只填了域名，按 OpenAI 兼容约定补 /v1
            return url + "/v1/chat/completions";
        }
        return defaultChatUrl(platform);
    }

    /**
     * 安全查找平台枚举：未知平台不抛异常（避免把「模型配置有误」变成 500），返回 null。
     */
    private AiPlatformEnum findPlatform(String platform) {
        if (StrUtil.isBlank(platform)) {
            return null;
        }
        for (AiPlatformEnum item : AiPlatformEnum.values()) {
            if (item.getPlatform().equals(platform)) {
                return item;
            }
        }
        return null;
    }

    /** 各平台官方的 OpenAI 兼容 chat/completions 地址 */
    private String defaultChatUrl(AiPlatformEnum platform) {
        if (platform == null) {
            return null;
        }
        return switch (platform) {
            case OPENAI -> "https://api.openai.com/v1/chat/completions";
            case DEEP_SEEK -> "https://api.deepseek.com/v1/chat/completions";
            case TONG_YI -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case ZHI_PU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case DOU_BAO -> "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
            case HUN_YUAN -> "https://api.hunyuan.cloud.tencent.com/v1/chat/completions";
            case SILICON_FLOW -> "https://api.siliconflow.cn/v1/chat/completions";
            case MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions";
            case MINI_MAX -> "https://api.minimax.chat/v1/chat/completions";
            case BAI_CHUAN -> "https://api.baichuan-ai.com/v1/chat/completions";
            case STEP_FUN -> "https://api.stepfun.com/v1/chat/completions";
            case GROK -> "https://api.x.ai/v1/chat/completions";
            case ANTHROPIC -> "https://api.anthropic.com/v1/chat/completions";
            case GEMINI -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            case YI_YAN -> "https://qianfan.baidubce.com/v2/chat/completions";
            case OLLAMA -> "http://127.0.0.1:11434/v1/chat/completions";
            default -> null;
        };
    }

}
