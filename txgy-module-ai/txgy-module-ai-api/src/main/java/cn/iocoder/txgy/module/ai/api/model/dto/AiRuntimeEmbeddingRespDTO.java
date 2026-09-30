package cn.iocoder.txgy.module.ai.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** AI 服务向量响应，包含实际命中的动态模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRuntimeEmbeddingRespDTO {
    private List<List<Float>> embeddings;
    private Integer dimensions;
    private Long modelId;
    private String modelName;
    private String model;
    private String platform;
}
