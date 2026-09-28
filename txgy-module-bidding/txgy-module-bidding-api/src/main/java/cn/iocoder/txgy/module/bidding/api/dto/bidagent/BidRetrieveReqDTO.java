package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 投标检索请求（Agent → Java，架构文档 §3.1）
 */
@Data
public class BidRetrieveReqDTO {

    /** 租户编号 */
    private Long tenantId;

    /** 项目编号 */
    private Long projectId;

    /** 检索词 */
    @NotBlank(message = "检索词不能为空")
    private String query;

    /** 过滤条件 */
    private Filters filters;

    /** 返回条数，默认 8 */
    private Integer topK;

    /** 相似度阈值（召回调试用；空=用配置 min-score） */
    private Double minScore;

    /** 检索模式：hybrid / vector / bm25（空=hybrid） */
    private String mode;

    /** 必检词列表：任一未命中则 hit=false */
    private List<String> mandatory;

    /** 检索过滤条件 */
    @Data
    public static class Filters {

        /** 文档类型 */
        private String docType;

        /** 有效期判定日期，格式 yyyy-MM-dd */
        private String validOn;

        /** 业务域 */
        private String domain;

        /** 文档级隔离：仅检索该来源文档（如"基于某招标文件生成"） */
        private String sourceDocName;

        /** 标段级隔离：仅检索该标段的材料（TENDER 材料必须绑标段，2026-09-16 起生效） */
        private String bidSectionCode;

        /**
         * 多标段检索：一次检索多个标段（长文本招标文件按标段拆分的场景）。
         *
         * <p>与 bidSectionCode 二选一，同时给出时以本列表为准；标段隔离只约束 TENDER，
         * 未绑标段的 FACT/TEMPLATE 是否放行由 bidding.bid-agent.retrieve.section-include-unbound 决定。
         */
        private List<String> bidSectionCodes;
    }
}
