package cn.iocoder.txgy.module.ai.api.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库检索命中的分段 DTO（RPC）
 *
 * <p>只回传「正文 + 相似度 + 归属」，足够业务模块拼出 RAG 提示词；
 * 不回传向量等内部字段，避免把 AI 模块的实现细节泄漏给业务方。
 */
@Schema(description = "RPC 服务 - 知识库检索命中的分段")
@Data
public class AiKnowledgeSegmentDTO {

    @Schema(description = "分段编号", example = "1024")
    private Long id;

    @Schema(description = "所属文档编号", example = "88")
    private Long documentId;

    @Schema(description = "所属知识库编号", example = "7")
    private Long knowledgeId;

    @Schema(description = "分段正文（即要交给大模型的资料原文）")
    private String content;

    @Schema(description = "相似度得分，越大越相关", example = "0.82")
    private Double score;

}
