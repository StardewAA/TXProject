package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import lombok.Data;

import java.util.List;

/**
 * 投标检索响应（§3.1）：hit=false 时 Agent 必须中止生成转人工
 */
@Data
public class BidRetrieveRespDTO {

    /** 命中片段 */
    private List<Chunk> chunks;

    /** 是否命中（含必检通道判定） */
    private Boolean hit;

    /** 检索命中片段 */
    @Data
    public static class Chunk {

        /** 片段编号 */
        private String chunkId;

        /** 片段内容 */
        private String content;

        /** 来源文档名 */
        private String sourceDocName;

        /** 标段编号（TENDER 材料绑定的标段；FACT/TEMPLATE 为空） */
        private String bidSectionCode;

        /** 章节路径 */
        private String chapterPath;

        /** 页码 */
        private Integer pageNo;

        /** 综合得分 */
        private Double score;

        /** 命中方式：vector / bm25 / mandatory / rerank */
        private String matchedBy;

        /** 溯源块编号 */
        private List<String> sourceBlockIds;
    }
}
