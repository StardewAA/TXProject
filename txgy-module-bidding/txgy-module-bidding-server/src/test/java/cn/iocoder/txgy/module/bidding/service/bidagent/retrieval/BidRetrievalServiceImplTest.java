package cn.iocoder.txgy.module.bidding.service.bidagent.retrieval;
// [依据] 标书检索关键词通道（文档 24 §5 待办 1）：向量 + 关键词双通道粗召回 → RRF 融合 → rerank → 必检；
//       bm25-enabled=false 必须与"仅向量 + rerank"完全等价（回滚开关）。

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveReqDTO;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveRespDTO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentChunkMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.BidAgentProperties;
import cn.iocoder.txgy.module.bidding.framework.bidagent.DashScopeRerankClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 检索实现单测（Mockito）：只验关键词通道的融合、命中方式与降级语义，云端与数据库全部打桩。
 */
class BidRetrievalServiceImplTest {

    private static final String DOC = "炼化公司2026年防腐保温项目施工招标技术文件.docx";

    private VectorStore vectorStore;
    private BidAgentChunkMapper chunkMapper;
    private ObjectProvider<VectorStore> vectorProvider;
    private ObjectProvider<DashScopeRerankClient> rerankProvider;
    private BidAgentProperties properties;
    private BidRetrievalServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        chunkMapper = mock(BidAgentChunkMapper.class);
        vectorProvider = mock(ObjectProvider.class);
        rerankProvider = mock(ObjectProvider.class);
        when(vectorProvider.getIfAvailable()).thenReturn(vectorStore);
        when(rerankProvider.getIfAvailable()).thenReturn(null); // 默认关闭 rerank，专测融合与命中方式
        properties = new BidAgentProperties();
        properties.setAiEnabled(true);
        BidAgentProperties.Retrieve retrieve = new BidAgentProperties.Retrieve();
        retrieve.setTopK(8);
        retrieve.setMinScore(0.35d);
        retrieve.setCandidateFactor(4);
        retrieve.setBm25Enabled(true);
        retrieve.setBm25TopK(8);
        retrieve.setRrfK(60);
        properties.setRetrieve(retrieve);
        service = newService();
    }

    /** 重建服务：rerank 客户端在构造期由 ObjectProvider 解析，打桩后必须重新 new 才能生效 */
    private BidRetrievalServiceImpl newService() {
        return new BidRetrievalServiceImpl(vectorProvider, rerankProvider, properties, chunkMapper);
    }

    @Test
    void bm25OnlyHit_isMarkedBm25() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of(chunkRow("v9", DOC + "#9", "最迟于 2026 年 11 月 30 日完工")));

        BidRetrieveRespDTO resp = service.retrieve(req("完工时间"));

        assertTrue(resp.getHit());
        assertEquals(2, resp.getChunks().size());
        BidRetrieveRespDTO.Chunk keywordOnly = chunkOf(resp, DOC + "#9");
        assertEquals("bm25", keywordOnly.getMatchedBy(), "关键词独占候选应标记 bm25");
        assertEquals(0.5d, keywordOnly.getScore(), 0.0001d, "BM25 原始分应归一化为 0~1 相关度分");
        assertEquals("vector", chunkOf(resp, DOC + "#1").getMatchedBy());
    }

    @Test
    void aiDisabled_strictRetrieveFallsBackToBm25WithSection() {
        properties.setAiEnabled(false);
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of(chunkRow("v9", DOC + "#9", "投标方需要编制施工组织设计")));

        BidRetrieveRespDTO resp = service.retrieveStrict(req("投标方需要编制施工组织设计吗"));

        assertTrue(resp.getHit());
        assertEquals(1, resp.getChunks().size());
        assertEquals("bm25", resp.getChunks().get(0).getMatchedBy());
        assertEquals("SEC-001", resp.getChunks().get(0).getBidSectionCode());
        assertEquals(0.5d, resp.getChunks().get(0).getScore(), 0.0001d);
        verifyNoInteractions(vectorStore);
    }
    @Test
    void bothChannelsHit_isMarkedHybrid_andDeduped() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        // 同一切片（vector_id 相同）同时被关键词通道召回 → 不能出现两条，且标记 hybrid
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of(chunkRow("v1", DOC + "#1", "履约保证金按合同金额 5% 提交")));

        BidRetrieveRespDTO resp = service.retrieve(req("履约保证金"));

        assertEquals(1, resp.getChunks().size(), "同一 vector_id 的候选必须去重");
        assertEquals("hybrid", resp.getChunks().get(0).getMatchedBy());
    }

    @Test
    void bm25Disabled_keepsVectorOnlyBehaviour() {
        properties.getRetrieve().setBm25Enabled(false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));

        BidRetrieveRespDTO resp = service.retrieve(req("履约保证金"));

        assertEquals(1, resp.getChunks().size());
        assertEquals("vector", resp.getChunks().get(0).getMatchedBy());
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void bm25Failure_fallsBackToVectorOnly() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenThrow(new RuntimeException("Unknown index ft_chunk_content"));

        BidRetrieveRespDTO resp = service.retrieve(req("履约保证金"));

        assertTrue(resp.getHit(), "关键词通道异常不应把整条召回链打挂");
        assertEquals(1, resp.getChunks().size());
        assertEquals("vector", resp.getChunks().get(0).getMatchedBy());
    }

    @Test
    void lowVectorScoreWithoutKeyword_droppedByThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.10d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of());

        BidRetrieveRespDTO resp = service.retrieve(req("与标段无关的问题"));

        assertFalse(resp.getHit(), "低于 min-score 且无关键词命中时应维持 hit=false");
        assertTrue(resp.getChunks().isEmpty());
    }

    @Test
    void rerankEnabled_isMarkedRerank() {
        DashScopeRerankClient rerankClient = mock(DashScopeRerankClient.class);
        when(rerankClient.isEnabled()).thenReturn(true);
        when(rerankProvider.getIfAvailable()).thenReturn(rerankClient);
        service = newService();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of(chunkRow("v9", DOC + "#9", "质量保证期 24 个月")));
        when(rerankClient.rerank(anyString(), any()))
                .thenReturn(List.of(new DashScopeRerankClient.RerankResult(1, 0.88d)));

        BidRetrieveRespDTO resp = service.retrieve(req("质量保证期"));

        assertEquals(1, resp.getChunks().size());
        BidRetrieveRespDTO.Chunk chunk = resp.getChunks().get(0);
        assertEquals("rerank", chunk.getMatchedBy());
        assertEquals(0.88d, chunk.getScore(), 1e-6, "rerank 命中时分数取 rerank 分");
        assertEquals(DOC + "#9", chunk.getSourceBlockIds().get(0), "重排后可把关键词候选提到首位");
    }

    private BidRetrieveReqDTO req(String query) {
        BidRetrieveReqDTO dto = new BidRetrieveReqDTO();
        dto.setTenantId(1L);
        dto.setProjectId(900001L);
        dto.setQuery(query);
        BidRetrieveReqDTO.Filters filters = new BidRetrieveReqDTO.Filters();
        filters.setDocType("TENDER");
        filters.setBidSectionCode("SEC-001");
        dto.setFilters(filters);
        return dto;
    }

    // ========== 元数据过滤下推（2026-09-21）==========

    @Test
    void metadataFilter_isPushedDownToVectorStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of());

        service.retrieve(req("履约保证金"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest sent = captor.getValue();
        assertNotNull(sent.getFilterExpression(), "租户/项目/标段等元数据必须下推到向量库，而不是搜完再过滤");
        String expression = sent.getFilterExpression().toString();
        assertTrue(expression.contains("tenantId"), expression);
        assertTrue(expression.contains("bidSectionCode"), expression);
        assertTrue(expression.contains("docType"), expression);
    }

    @Test
    void sectionFilter_keepsUnboundFactButDropsOtherSections() {
        BidRetrieveReqDTO request = req("防腐保温专业承包资质");
        request.getFilters().setDocType(null);   // 不限文档类型：企业资料（FACT）也要能召回
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorDoc("v1", DOC + "#1", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v2", DOC + "#2", 0.9d, "SEC-002", "TENDER"),
                vectorDoc("v3", "资质证书.pdf#1", 0.9d, null, "FACT")));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of());

        BidRetrieveRespDTO resp = service.retrieve(request);

        assertEquals(2, resp.getChunks().size(), "目标标段 + 未绑标段的企业资料要留下，其他标段要滤掉");
        assertTrue(resp.getChunks().stream().anyMatch(chunk -> "SEC-001".equals(chunk.getBidSectionCode())),
                "目标标段片段应命中且带标段号");
        assertTrue(resp.getChunks().stream().anyMatch(chunk -> chunk.getBidSectionCode() == null),
                "未绑标段的企业资料应保留（跨标段复用）");
    }

    @Test
    void sectionBalance_preventsSingleSectionDominatingTopK() {
        BidRetrieveReqDTO request = req("质量保证期");
        request.getFilters().setBidSectionCode(null);   // 不限标段：期望各标段都有代表
        request.setTopK(4);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorDoc("v1", DOC + "#1", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v2", DOC + "#2", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v3", DOC + "#3", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v4", DOC + "#4", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v5", DOC + "#5", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v6", DOC + "#6", 0.9d, "SEC-001", "TENDER"),
                vectorDoc("v7", DOC + "#7", 0.9d, "SEC-002", "TENDER"),
                vectorDoc("v8", DOC + "#8", 0.9d, "SEC-002", "TENDER")));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of());

        BidRetrieveRespDTO resp = service.retrieve(request);

        assertEquals(4, resp.getChunks().size(), "返回条数不因均衡占额而减少");
        long section1 = resp.getChunks().stream().filter(chunk -> "SEC-001".equals(chunk.getBidSectionCode())).count();
        long section2 = resp.getChunks().stream().filter(chunk -> "SEC-002".equals(chunk.getBidSectionCode())).count();
        assertEquals(2, section1, "单标段占额不超过 ceil(topK/标段数)");
        assertEquals(2, section2, "另一标段必须拿到名额");
    }

    @Test
    void sourceBlockIds_areTrimmedOnSplit() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(vectorDoc("v1", DOC + "#1", 0.9d)));
        when(chunkMapper.selectByKeyword(anyString(), anyLong(), any(), any(), anyBoolean(), isNull(), anyInt()))
                .thenReturn(List.of(chunkRow("v9", DOC + "#9,  " + DOC + "#10", "质量保证期 24 个月")));

        BidRetrieveRespDTO resp = service.retrieve(req("质量保证期"));

        assertEquals(List.of(DOC + "#9", DOC + "#10"), chunkOf(resp, DOC + "#9").getSourceBlockIds(),
                "块号拆分要 trim，避免带空格污染溯源比对");
    }

    /** 向量候选（可指定标段/文档类型，section=null 表示未绑标段的材料） */
    private Document vectorDoc(String id, String blockIds, double score, String section, String docType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", 1);
        metadata.put("projectId", 900001);
        metadata.put("docType", docType);
        if (section != null) {
            metadata.put("bidSectionCode", section);
        }
        metadata.put("sourceDocName", DOC);
        metadata.put("sourceBlockIds", blockIds);
        return Document.builder().id(id).text("向量召回片段 " + blockIds).metadata(metadata).score(score).build();
    }

    /** 向量候选：metadata 键与入库侧一致，租户/项目/文档类型/标段都要能通过应用层过滤 */
    private Document vectorDoc(String id, String blockIds, double score) {
        return Document.builder()
                .id(id)
                .text("向量召回片段 " + blockIds)
                .metadata(Map.of(
                        "tenantId", 1,
                        "projectId", 900001,
                        "docType", "TENDER",
                        "bidSectionCode", "SEC-001",
                        "sourceDocName", DOC,
                        "sourceBlockIds", blockIds))
                .score(score)
                .build();
    }

    private BidAgentChunkDO chunkRow(String vectorId, String blockIds, String content) {
        BidAgentChunkDO row = new BidAgentChunkDO();
        row.setVectorId(vectorId);
        row.setProjectId(900001L);
        row.setDocType("TENDER");
        row.setBidSectionCode("SEC-001");
        row.setSourceDocName(DOC);
        row.setSourceBlockIds(blockIds);
        row.setContent(content);
        row.setTenantId(1L);
        row.setKeywordScore(1.0d);
        return row;
    }

    private static BidRetrieveRespDTO.Chunk chunkOf(BidRetrieveRespDTO resp, String blockId) {
        List<BidRetrieveRespDTO.Chunk> matched = new ArrayList<>();
        for (BidRetrieveRespDTO.Chunk chunk : resp.getChunks()) {
            if (chunk.getSourceBlockIds() != null && chunk.getSourceBlockIds().contains(blockId)) {
                matched.add(chunk);
            }
        }
        assertEquals(1, matched.size(), "期望恰好命中一条：" + blockId);
        return matched.get(0);
    }
}
