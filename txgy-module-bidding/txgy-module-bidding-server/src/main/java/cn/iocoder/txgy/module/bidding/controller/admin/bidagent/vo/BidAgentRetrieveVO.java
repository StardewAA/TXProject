package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Schema(description = "标书智能体 - 检索 VO（后台召回验证与前台共用）")
public class BidAgentRetrieveVO {

    @Schema(description = "检索入参（BidAgentRetrieveReqVO）")
    @Data
    public static class ReqVO {
        @Schema(description = "检索词", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "检索词不能为空")
        private String query;
        @Schema(description = "材料角色过滤：TENDER/FACT/TEMPLATE，空=不限")
        private String docType;
        @Schema(description = "标段编号过滤，空=不限")
        private String bidSectionCode;
        @Schema(description = "多标段过滤（与 bidSectionCode 二选一，同时给出时以本列表为准）")
        private List<String> bidSectionCodes;
        @Schema(description = "返回条数（空=底座默认）")
        private Integer topK;
        @Schema(description = "相似度阈值（空=用配置 min-score），用于召回调试")
        private Double minScore;
        @Schema(description = "检索模式：hybrid 向量+关键词 / vector 仅向量 / bm25 仅关键词；空=hybrid")
        private String mode;
        @Schema(description = "必检词（任一未命中则 hit=false，前台应转人工）")
        private List<String> mandatory;
    }

    @Schema(description = "检索出参（BidAgentRetrieveRespVO）")
    @Data
    public static class RespVO {
        /** 是否命中；false 时前台必须转人工，不得继续生成 */
        private Boolean hit;
        private List<Chunk> chunks;
        /** 本次检索耗时（毫秒） */
        private Long elapsedMs;
    }

    @Schema(description = "命中片段（BidAgentRetrieveRespVO.Chunk）")
    @Data
    public static class Chunk {
        private String chunkId;
        private String content;
        private String sourceDocName;
        @Schema(description = "标段编号（TENDER 材料绑定的标段；FACT/TEMPLATE 为空）")
        private String bidSectionCode;
        private String chapterPath;
        private Integer pageNo;
        private Double score;
        /** 命中方式：vector 向量 / bm25 关键词 / rerank 重排 */
        private String matchedBy;
        private List<String> sourceBlockIds;
    }
}
