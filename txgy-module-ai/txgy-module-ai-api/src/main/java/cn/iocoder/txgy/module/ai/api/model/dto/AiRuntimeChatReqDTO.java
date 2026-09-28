package cn.iocoder.txgy.module.ai.api.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** CMS 等业务模块调用 AI 服务的非流式对话请求。 */
@Data
public class AiRuntimeChatReqDTO {

    /** 业务场景编码；配置后由 ai_model_scene_route 选择主、备模型。 */
    @Size(max = 64)
    private String sceneCode;

    /** 为空时使用 AI 模块中已启用、排序最前的对话模型。 */
    private Long modelId;

    @Size(max = 8000)
    private String systemPrompt;

    @NotBlank
    @Size(max = 200000)
    private String userPrompt;

    @Min(0)
    @Max(2)
    private Double temperature;

    @Min(1)
    @Max(32000)
    private Integer maxTokens;
}
