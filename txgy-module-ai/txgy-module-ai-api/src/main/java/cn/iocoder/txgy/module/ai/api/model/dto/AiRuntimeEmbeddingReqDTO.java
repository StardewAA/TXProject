package cn.iocoder.txgy.module.ai.api.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** CMS 等业务模块调用 AI 服务的向量请求。 */
@Data
public class AiRuntimeEmbeddingReqDTO {

    /** 业务场景编码；配置后由 ai_model_scene_route 选择主、备模型。 */
    @Size(max = 64)
    private String sceneCode;

    /** 为空时使用 AI 模块中已启用、排序最前的向量模型。 */
    private Long modelId;

    @NotEmpty
    @Size(max = 20)
    private List<@Size(max = 20000) String> texts;
}
