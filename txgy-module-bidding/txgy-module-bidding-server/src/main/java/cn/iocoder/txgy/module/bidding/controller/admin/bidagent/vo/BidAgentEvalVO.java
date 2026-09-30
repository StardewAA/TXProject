package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "标书智能体 - 精准度测试 VO")
public class BidAgentEvalVO {

    @Schema(description = "用例分页入参（BidAgentEvalCasePageReqVO）")
    @Data
    public static class CasePageReqVO extends PageParam {
        @Schema(description = "用例名称（模糊）")
        private String name;
        @Schema(description = "是否启用：1 是 0 否")
        private Integer enabled;
    }

    @Schema(description = "用例保存入参（BidAgentEvalCaseSaveReqVO）")
    @Data
    public static class CaseSaveReqVO {
        @Schema(description = "编号（新增留空）")
        private Long id;
        @Schema(description = "用例名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "用例名称不能为空")
        private String name;
        @Schema(description = "标准问题（检索词）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "标准问题不能为空")
        private String query;
        @Schema(description = "限定材料角色：TENDER/FACT/TEMPLATE，空=不限")
        private String docType;
        @Schema(description = "限定标段编号，空=不限")
        private String bidSectionCode;
        @Schema(description = "评测取前 K 条（默认 10）")
        private Integer topK;
        @Schema(description = "期望命中的关键词，半角逗号分隔")
        private String expectKeywords;
        @Schema(description = "期望命中的溯源块编号，半角逗号分隔（可选）")
        private String expectBlockIds;
        @Schema(description = "是否启用：1 是 0 否")
        private Integer enabled;
        @Schema(description = "备注")
        private String remark;
    }

    @Schema(description = "用例出参（BidAgentEvalCaseRespVO）")
    @Data
    public static class CaseRespVO {
        private Long id;
        private String name;
        private String query;
        private String docType;
        private String bidSectionCode;
        private Integer topK;
        private String expectKeywords;
        private String expectBlockIds;
        private Integer enabled;
        private String remark;
        private LocalDateTime createTime;
    }

    @Schema(description = "跑批入参（BidAgentEvalRunReqVO）：空列表=跑全部启用用例")
    @Data
    public static class RunReqVO {
        @Schema(description = "指定用例编号列表（空=全部启用用例）")
        private List<Long> caseIds;
        @Schema(description = "覆盖用例的取前 K 条（空=按用例自身 topK）")
        private Integer topK;
    }

    @Schema(description = "跑批出参（BidAgentEvalRunRespVO）")
    @Data
    public static class RunRespVO {
        private Integer totalCount;
        private Integer hitCount;
        /** 召回率（命中用例数 / 用例总数，保留 4 位小数） */
        private Double recall;
        /** 用例命中率（仅统计已标注期望标准的用例） */
        private Double caseHitRate;
        private Integer evaluatedCount;
        private Integer unlabeledCount;
        private Boolean scoreAvailable;
        /** rerank / vector / mixed / bm25 / none */
        private String scoreType;
        private Integer scoredCaseCount;
        private Integer unscoredCaseCount;
        private Map<String, Integer> channelCounts;
        /** 平均命中分数（命中用例最佳片段分数的均值，保留 4 位小数） */
        private Double avgScore;
        /** 平均检索耗时（毫秒） */
        private Long avgLatencyMs;
        /** 最长检索耗时（毫秒） */
        private Long maxLatencyMs;
        /** 本次跑批使用的 K */
        private Integer topK;
        private List<CaseResultVO> cases;
    }

    @Schema(description = "单用例结果（BidAgentEvalRunRespVO.CaseResultVO）")
    @Data
    public static class CaseResultVO {
        private Long caseId;
        private String name;
        private String query;
        private Boolean evaluated;
        private Boolean hit;
        /** rerank / vector / bm25 / none */
        private String matchedBy;
        /** 命中的溯源块编号（用于定位"命中了但命错地方"） */
        private List<String> matchedBlockIds;
        /** 未命中原因（hit=false 时说明是没资料还是切片问题） */
        private String missReason;
        /** 最佳命中片段分数（无命中为 null） */
        private Double score;
        /** 本用例检索耗时（毫秒） */
        private Integer topK;
        private Long latencyMs;
    }
}
