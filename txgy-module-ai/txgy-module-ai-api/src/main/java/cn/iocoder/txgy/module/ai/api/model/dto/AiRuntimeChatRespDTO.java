package cn.iocoder.txgy.module.ai.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 服务对话响应，包含实际命中的动态模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRuntimeChatRespDTO {
    private String content;
    private Long modelId;
    private String modelName;
    private String model;
    private String platform;
}
