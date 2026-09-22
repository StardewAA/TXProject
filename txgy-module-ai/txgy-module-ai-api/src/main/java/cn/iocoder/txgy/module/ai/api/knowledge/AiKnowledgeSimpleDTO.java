package cn.iocoder.txgy.module.ai.api.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库简要信息 DTO（RPC）
 */
@Schema(description = "RPC 服务 - 知识库简要信息")
@Data
public class AiKnowledgeSimpleDTO {

    @Schema(description = "知识库编号", example = "7")
    private Long id;

    @Schema(description = "知识库名称", example = "公司仓储制度")
    private String name;

}
