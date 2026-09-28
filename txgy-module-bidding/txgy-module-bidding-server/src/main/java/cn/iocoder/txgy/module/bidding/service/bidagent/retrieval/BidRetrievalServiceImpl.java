package cn.iocoder.txgy.module.bidding.service.bidagent.retrieval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveReqDTO;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveRespDTO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentChunkMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.BidAgentProperties;
import cn.iocoder.txgy.module.bidding.framework.bidagent.DashScopeRerankClient;
import cn.iocoder.txgy.module.bidding.service.bidagent.index.BidTextCleaner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_RETRIEVE_QUERY_EMPTY;
import static org.springframework.ai.vectorstore.SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;

/**
 * 投标检索实现。
 *
 * 设计要点（架构文档 §5）：
 * 1. 不用 Spring AI 的 RetrievalAugmentationAdvisor，显式编排「向量检索 → rerank → 阈值闸门 → 必检通道 → 溯源」；
 * 2. 多租户隔离 = 向量库按租户入库存放 + 应用层按 metadata(tenantId/projectId/docType) 二次过滤
 *    （避免依赖不同 Spring AI 版本的 FilterExpressionBuilder API 差异）；
 * 3. 无模型/无向量库时返回 hit=false，让 Agent 转人工，而不是硬失败。
 */
@Service
@Slf4j
public class BidRetrievalServiceImpl implements BidRetrievalService {

    /** 向量 metadata 字段约定（与入库侧保持一致） */
    public static final String MD_TENANT_ID = "tenantId";
    public static final String MD_PROJECT_ID = "projectId";
    public static final String MD_SOURCE_DOC = "sourceDocName";
    public static final String MD_CHAPTER = "chapterPath";
    public static final String MD_PAGE = "pageNo";
    public static final String MD_BLOCK_IDS = "sourceBlockIds";
    public static final String MD_DOC_TYPE = "docType";
    /** 标段编号：TENDER 类材料按标段隔离（2026-09-16 起生效） */
    public static final String MD_SECTION = "bidSectionCode";
    /** 命中方式：向量通道 */
    private static final String CHANNEL_VECTOR = "vector";
    /** 命中方式：关键词通道（BM25 / MySQL FULLTEXT + ngram） */
    private static final String CHANNEL_KEYWORD = "bm25";
/** 检索模式：仅关键词（不依赖向量库/云端） */
private static final String MODE_BM25 = "bm25";
/** 检索模式：仅向量（跳过关键词通道） */
private static final String MODE_VECTOR = "vector";
/** 招标文件类型：标段隔离只约束它；企业资料/技术母本可跨标段复用 */
private static final String DOC_TYPE_TENDER = "TENDER";

    private final VectorStore vectorStore;
    private final DashScopeRerankClient rerankClient;
    private final BidAgentProperties properties;
    private final BidAgentChunkMapper chunkMapper;

    public BidRetrievalServiceImpl(ObjectProvider<VectorStore> vectorStoreProvider,
                                   ObjectProvider<DashScopeRerankClient> rerankClientProvider,
                                   BidAgentProperties properties,
                                   BidAgentChunkMapper chunkMapper) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.rerankClient = rerankClientProvider.getIfAvailable();
        this.properties = properties;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public BidRetrieveRespDTO retrieve(BidRetrieveReqDTO reqDTO) {
        // 契约端（Node 侧 bid-agent-service 调用）：云端不可用/向量库异常时降级为 hit=false 让 Agent 转人工，
        // 而不是让整条编排硬失败（设计要点 3）。真正原因仍落 WARN 日志 + 错误码，便于运维定位。
        try {
            return doRetrieve(reqDTO);
        } catch (Exception ex) {
            log.warn("[retrieve][云端检索不可用] query={} reason={}",
                    reqDTO == null ? null : reqDTO.getQuery(), ex.getMessage());
            // 云端（embedding/rerank）欠费、限流等故障时回退关键词通道：BM25 走 MySQL FULLTEXT，
            // 不依赖云端，保证检索链路可用（质量降为关键词级，matchedBy=bm25 供前端标识），
            // 避免生成链因必检项全部未命中而整条转人工。
            BidRetrieveRespDTO fallback = tryKeywordFallback(reqDTO);
            return fallback != null ? fallback : emptyHit();
        }
    }

    /**
     * 云端不可用时的关键词通道兜底。
     *
     * <p>返回 null 表示兜底不可用（bm25 开关关闭 / 切片表未配置 / 兜底自身失败），
     * 由调用方按原契约降级为 hit=false。
     */
    private BidRetrieveRespDTO tryKeywordFallback(BidRetrieveReqDTO reqDTO) {
        BidAgentProperties.Retrieve config = properties.getRetrieve();
        if (config == null || !Boolean.TRUE.equals(config.getBm25Enabled()) || chunkMapper == null) {
            return null;
        }
        try {
            BidRetrieveRespDTO resp = doKeywordOnly(reqDTO);
            log.warn("[retrieve][云端不可用，已回退关键词通道] query={} hit={} chunks={}",
                    reqDTO == null ? null : reqDTO.getQuery(),
                    resp.getHit(), resp.getChunks().size());
            return resp;
        } catch (Exception ex) {
            log.warn("[retrieve][关键词通道兜底失败，按原契约降级] query={} msg={}",
                    reqDTO == null ? null : reqDTO.getQuery(), ex.getMessage());
            return null;
        }
    }

    @Override
    public BidRetrieveRespDTO retrieveStrict(BidRetrieveReqDTO reqDTO) {
        // 后台召回验证/跑批与契约端保持同一降级口径：
        // 云端不可用时优先回退 BM25，仍不可用才返回 hit=false，避免把配置故障误判成资料未入库。
        return retrieve(reqDTO);
    }

    private static BidRetrieveRespDTO emptyHit() {
        BidRetrieveRespDTO resp = new BidRetrieveRespDTO();
        resp.setHit(false);
        resp.setChunks(new ArrayList<>());
        return resp;
    }

    private BidRetrieveRespDTO doRetrieve(BidRetrieveReqDTO reqDTO) {
        BidRetrieveRespDTO resp = new BidRetrieveRespDTO();
        resp.setChunks(new ArrayList<>());

        // 契约入参兜底：上游传 null 时直接 NPE 会绕过 retrieve() 的 ServiceException 降级，改为业务异常
        if (reqDTO == null || StrUtil.isBlank(reqDTO.getQuery())) {
            throw exception(BID_RETRIEVE_QUERY_EMPTY);
        }
        // 检索词与入库侧同口径做编码统一（全角→半角、去零宽/不可见空白）：
        // 入库文本已按 BidTextCleaner 归一，检索词若原样下发，会出现“资料里有、就是搜不到”的假未命中
        String query = BidTextCleaner.normalizeEncoding(reqDTO.getQuery());
        if (StrUtil.isBlank(query)) {
            throw exception(BID_RETRIEVE_QUERY_EMPTY);
        }

        // 检索模式与阈值：召回调试可临时覆盖配置，便于专员在页面上一键对比效果
        String mode = StrUtil.nullToDefault(reqDTO.getMode(), "");
        double minScore = ObjUtil.defaultIfNull(reqDTO.getMinScore(), properties.getRetrieve().getMinScore());

        // 仅关键词模式：走 MySQL BM25 通道，不依赖向量库/云端（可离线调试关键词口径）
        if (MODE_BM25.equals(mode)) {
            return doKeywordOnly(reqDTO);
        }

        // 未启用 AI 或没有向量库：混合模式自动回退 BM25，显式 vector 模式仍保持严格向量语义
        if (!Boolean.TRUE.equals(properties.getAiEnabled()) || vectorStore == null) {
            log.warn("[retrieve][向量检索不可用，尝试 BM25 回退] query={}", query);
            if (MODE_VECTOR.equals(mode)) {
                resp.setHit(false);
                return resp;
            }
            BidRetrieveRespDTO fallback = tryKeywordFallback(reqDTO);
            return fallback == null ? emptyHit() : fallback;
        }

        int topK = ObjUtil.defaultIfNull(reqDTO.getTopK(), properties.getRetrieve().getTopK());
        int candidateFactor = properties.getRetrieve().getCandidateFactor();
        // 文档级检索（filters.sourceDocName 非空）时扩大粗召回候选，避免目标文档片段在 topK 之外被漏掉
        boolean docScoped = reqDTO.getFilters() != null && StrUtil.isNotBlank(reqDTO.getFilters().getSourceDocName());
        int candidateTopK = topK * candidateFactor * (docScoped ? 3 : 1);

        // 1. 向量检索（放宽阈值，多取候选交给 rerank 重排）
        //    元数据过滤下推到向量库：租户/项目/文档类型/来源文档/标段在库侧过滤。
        //    否则"先全库取 candidateTopK 再应用层过滤"会把目标标段的片段挤出候选窗口（多标段语料尤其明显）
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(query)
                .topK(candidateTopK)
                .similarityThreshold(SIMILARITY_THRESHOLD_ACCEPT_ALL);
        Filter.Expression vectorFilter = buildVectorFilter(reqDTO);
        if (vectorFilter != null) {
            searchBuilder.filterExpression(vectorFilter);
        }
        List<Document> documents = vectorStore.similaritySearch(searchBuilder.build());
        if (CollUtil.isEmpty(documents)) {
            resp.setHit(false);
            return resp;
        }
        // 2. 应用层兜底过滤：与下推条件同一口径，防止向量库过滤器实现差异（或旧集合缺 metadata）破坏隔离
        documents = filterByScope(documents, reqDTO);
        if (CollUtil.isEmpty(documents)) {
            resp.setHit(false);
            return resp;
        }

// 3. 关键词通道（BM25 / MySQL FULLTEXT + ngram）粗召回：只在向量链路可用时作**补充**通道；
//    vector 模式跳过，hybrid 模式参与 RRF 融合；检索词沿用与入库侧一致的编码归一结果。
List<BidAgentChunkDO> keywordRows = MODE_VECTOR.equals(mode) ? List.of() : searchByKeyword(reqDTO, query);

        // 4. RRF 融合：向量 rank 与关键词 rank 各自算 1/(k+rank) 后相加（三种分数不可比，故用排名融合而非分数加权）
        int rrfK = ObjUtil.defaultIfNull(properties.getRetrieve().getRrfK(), 60);
        List<Candidate> candidates = fuseCandidates(documents, keywordRows, rrfK);

// 5. Rerank 重排序 + 阈值闸门：先拿到“有序且过阈值”的候选，不在此处截断 topK
        boolean reranked = rerankClient != null && rerankClient.isEnabled();
        List<Candidate> ranked = reranked
                ? rankByRerank(query, candidates, minScore)
                : rankByRrf(candidates, minScore);
        // rerank 对超长表格类片段易整体低分：候选非空却全被阈值滤掉时，回退 RRF/向量排序，
        // 避免把本可命中的片段误杀成 hit=false（弱 rerank 模型退化保护）
        if (reranked && CollUtil.isEmpty(ranked)) {
            log.warn("[retrieve][rerank 全部低于阈值({})，回退向量排序] query={}, candidates={}",
                    minScore, query, candidates.size());
            ranked = rankByRrf(candidates, minScore);
        }

        // 6. 取前 topK：未指定标段且候选跨标段时按标段均衡占额（多标段语料防单标段霸榜）
        boolean balanceAcrossSections = sectionsOf(reqDTO.getFilters()).isEmpty();
        List<Candidate> selected = selectTopK(ranked, topK, balanceAcrossSections);

        // 7. 组装溯源片段
        List<BidRetrieveRespDTO.Chunk> chunks = new ArrayList<>();
        for (Candidate candidate : selected) {
            chunks.add(toChunk(candidate, reranked));
        }

        // 5. 必检通道：任一必检词未命中 → hit=false
        List<String> missing = checkMandatory(reqDTO.getMandatory(), chunks);
        if (CollUtil.isNotEmpty(missing)) {
            String probe = chunks.isEmpty() ? "NA" : String.valueOf(chunks.get(0).getContent());
            String sample = probe.length() > 40 ? probe.substring(0, 40) : probe;
            log.warn("[retrieve][必检项未命中] query={}, mandatory={}, missing={}, chunk0Has={}, sample={}",
                    query, reqDTO.getMandatory(), missing,
                    chunks.isEmpty() ? "na" : chunks.get(0).getContent().contains(missing.get(0)), sample);
            resp.setHit(false);
            resp.setChunks(chunks);
            return resp;
        }

        resp.setHit(CollUtil.isNotEmpty(chunks));
        resp.setChunks(chunks);
        return resp;
    }

    /**
     * 仅关键词通道（bm25）：不依赖向量库/云端，按 MySQL FULLTEXT 相关度降序取前 topK。
     *
     * <p>关键词通道无余弦分，min-score（余弦阈值）语义不适用，故不设阈值闸门，只做 topK 截断，
     * 与 {@link #rankByRrf} 对「关键词独占候选」跳过阈值过滤的口径一致。
     */
    private BidRetrieveRespDTO doKeywordOnly(BidRetrieveReqDTO reqDTO) {
        BidRetrieveRespDTO resp = new BidRetrieveRespDTO();
        resp.setChunks(new ArrayList<>());
        int topK = ObjUtil.defaultIfNull(reqDTO.getTopK(), properties.getRetrieve().getTopK());
        for (BidAgentChunkDO row : searchByKeyword(reqDTO, topK)) {
            resp.getChunks().add(toChunkFromRow(row));
        }
        List<String> missing = checkMandatory(reqDTO.getMandatory(), resp.getChunks());
        if (CollUtil.isNotEmpty(missing)) {
            resp.setHit(false);
            return resp;
        }
        resp.setHit(CollUtil.isNotEmpty(resp.getChunks()));
        return resp;
    }

    /** 关键词通道切片行 → 出参片段（无向量分，matchedBy 固定 bm25） */
    private BidRetrieveRespDTO.Chunk toChunkFromRow(BidAgentChunkDO row) {
        BidRetrieveRespDTO.Chunk chunk = new BidRetrieveRespDTO.Chunk();
        chunk.setChunkId(StrUtil.isNotBlank(row.getVectorId()) ? row.getVectorId() : row.getSourceBlockIds());
        chunk.setContent(row.getContent());
        chunk.setSourceDocName(row.getSourceDocName());
        chunk.setBidSectionCode(row.getBidSectionCode());
        chunk.setChapterPath(row.getChapterPath());
        chunk.setPageNo(row.getPageNo());
        chunk.setSourceBlockIds(split(row.getSourceBlockIds()));
        chunk.setMatchedBy(CHANNEL_KEYWORD);
        chunk.setScore(normalizeKeywordScore(row.getKeywordScore()));
        return chunk;
    }

    private List<Document> filterByScope(List<Document> documents, BidRetrieveReqDTO reqDTO) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            Map<String, Object> md = doc.getMetadata();
            if (reqDTO.getTenantId() != null
                    && !eqMeta(md.get(MD_TENANT_ID), reqDTO.getTenantId().toString())) {
                continue;
            }
            // projectId=0 表示"无需选项目的一键生成"，入库时未写项目元数据，这里跳过项目过滤
            if (reqDTO.getProjectId() != null && reqDTO.getProjectId() != 0L
                    && !eqMeta(md.get(MD_PROJECT_ID), reqDTO.getProjectId().toString())) {
                continue;
            }
            if (reqDTO.getFilters() != null && StrUtil.isNotBlank(reqDTO.getFilters().getDocType())
                    && !eqMeta(md.get(MD_DOC_TYPE), reqDTO.getFilters().getDocType())) {
                continue;
            }
            if (reqDTO.getFilters() != null && StrUtil.isNotBlank(reqDTO.getFilters().getSourceDocName())
                    && !eqMeta(md.get(MD_SOURCE_DOC), reqDTO.getFilters().getSourceDocName())) {
                continue;
            }
            // 标段隔离：TENDER 材料必须绑标段；未绑标段的 FACT/TEMPLATE 是否放行见 section-include-unbound
            List<String> sections = sectionsOf(reqDTO.getFilters());
            if (!sections.isEmpty() && !matchesSection(md, sections)) {
                continue;
            }
            result.add(doc);
        }
        return result;
    }

    private static boolean eqMeta(Object value, String expected) {
        return value != null && expected.equals(value.toString());
    }

    /**
     * 构建向量库侧过滤表达式（元数据下推）。
     *
     * <p>为什么要下推：向量库只返回 topK，若先全库取候选再在应用层过滤，多标段/多租户语料里
     * 目标标段的片段很容易被其他标段挤出候选窗口，表现为"资料在库里、检索却未命中"。
     *
     * <p>标段条件写成 {@code in(标段列表) or docType != TENDER}：标段隔离只约束 TENDER 招标文件，
     * 企业资料/技术母本不绑标段且需要跨标段复用（{@code section-include-unbound=false} 时收紧为严格隔离）。
     * 注意：Spring AI 的 Qdrant 过滤器支持 eq/ne/in/nin/gt/lt 等，但不支持 isNull，
     * 故这里用 ne(docType, TENDER) 表达"未绑标段的资料"，而不是 isNull(bidSectionCode)。
     *
     * @return null 表示没有任何可下推的条件（此时不做过滤，保持原行为）
     */
    private Filter.Expression buildVectorFilter(BidRetrieveReqDTO reqDTO) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        if (reqDTO.getTenantId() != null) {
            conditions.add(builder.eq(MD_TENANT_ID, reqDTO.getTenantId().toString()));
        }
        // projectId=0 与 null 同为「无需选项目的一键生成」，入库侧不写 projectId 元数据
        // （filterByScope / searchByKeyword 同口径：0 跳过项目过滤），这里也必须跳过，
        // 否则 eq(projectId,"0") 会把没有 projectId 字段的点全部过滤掉 → 向量检索恒空 → 必检项全未命中
        if (reqDTO.getProjectId() != null && reqDTO.getProjectId() != 0L) {
            conditions.add(builder.eq(MD_PROJECT_ID, reqDTO.getProjectId().toString()));
        }
        BidRetrieveReqDTO.Filters filters = reqDTO.getFilters();
        if (filters != null) {
            if (StrUtil.isNotBlank(filters.getDocType())) {
                conditions.add(builder.eq(MD_DOC_TYPE, filters.getDocType().trim()));
            }
            if (StrUtil.isNotBlank(filters.getSourceDocName())) {
                conditions.add(builder.eq(MD_SOURCE_DOC, filters.getSourceDocName().trim()));
            }
            List<String> sections = sectionsOf(filters);
            if (!sections.isEmpty()) {
                FilterExpressionBuilder.Op inSection = builder.in(MD_SECTION, new ArrayList<Object>(sections));
                conditions.add(sectionIncludeUnbound()
                        ? builder.or(inSection, builder.ne(MD_DOC_TYPE, DOC_TYPE_TENDER))
                        : inSection);
            }
        }
        if (conditions.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder.Op combined = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            combined = builder.and(combined, conditions.get(i));
        }
        return combined.build();
    }

    /** 有效标段列表：bidSectionCodes 优先，缺省回退单个 bidSectionCode；顺带 trim 与去空 */
    private static List<String> sectionsOf(BidRetrieveReqDTO.Filters filters) {
        List<String> sections = new ArrayList<>();
        if (filters == null) {
            return sections;
        }
        if (CollUtil.isNotEmpty(filters.getBidSectionCodes())) {
            for (String code : filters.getBidSectionCodes()) {
                if (StrUtil.isNotBlank(code)) {
                    sections.add(code.trim());
                }
            }
        }
        if (sections.isEmpty() && StrUtil.isNotBlank(filters.getBidSectionCode())) {
            sections.add(filters.getBidSectionCode().trim());
        }
        return sections;
    }

    /**
     * 片段是否通过标段过滤（与下推条件同口径的兜底判定）。
     *
     * <p>未绑标段的片段：默认放行非 TENDER（企业资料/技术母本跨标段复用）；
     * 严格模式（section-include-unbound=false）与 TENDER 未绑标段一并过滤掉。
     */
    private boolean matchesSection(Map<String, Object> md, List<String> sections) {
        String section = asString(md.get(MD_SECTION));
        if (StrUtil.isBlank(section)) {
            return sectionIncludeUnbound() && !DOC_TYPE_TENDER.equals(asString(md.get(MD_DOC_TYPE)));
        }
        return sections.contains(section.trim());
    }

    /** 标段过滤是否放行未绑标段的材料（默认 true） */
    private boolean sectionIncludeUnbound() {
        BidAgentProperties.Retrieve config = properties.getRetrieve();
        return config == null || !Boolean.FALSE.equals(config.getSectionIncludeUnbound());
    }

    /** 未指定标段时是否按标段均衡占额（默认 true） */
    private boolean sectionBalanceEnabled() {
        BidAgentProperties.Retrieve config = properties.getRetrieve();
        return config == null || !Boolean.FALSE.equals(config.getSectionBalanceEnabled());
    }

    /**
     * 从有序候选里取前 topK；未指定标段且候选跨标段时按标段均衡占额。
     *
     * <p>多标段语料里同一份招标文件按标段拆成 N 份，条款高度相似，纯按分数取前 topK 会被单个标段霸榜，
     * 让模型看不到其他标段的差异条款。这里给每个标段 ceil(topK/标段数) 的占额，
     * 额度没用满（其他标段没有更多候选）时再按原顺序二次补齐——只调整"谁占名额"，不减少返回条数、不改变分数顺序。
     *
     * <p>调用方已经指定标段时不启用均衡：此时全部候选都属于目标标段（外加未绑标段材料），
     * 再按组切额度会把目标标段的片段挤出去，反而降低召回。
     *
     * @param balanceAcrossSections 是否按标段均衡（仅在调用方未指定标段时传 true）
     */
    private List<Candidate> selectTopK(List<Candidate> ranked, int topK, boolean balanceAcrossSections) {
        if (ranked.size() <= topK) {
            return new ArrayList<>(ranked);
        }
        Map<String, Integer> sectionCount = new HashMap<>();
        for (Candidate candidate : ranked) {
            sectionCount.merge(sectionOf(candidate), 1, Integer::sum);
        }
        if (!balanceAcrossSections || !sectionBalanceEnabled() || sectionCount.size() <= 1) {
            return new ArrayList<>(ranked.subList(0, topK));
        }
        int cap = Math.max(1, (int) Math.ceil((double) topK / sectionCount.size()));
        boolean[] keep = new boolean[ranked.size()];
        Map<String, Integer> used = new HashMap<>();
        int kept = 0;
        for (int i = 0; i < ranked.size() && kept < topK; i++) {
            String section = sectionOf(ranked.get(i));
            if (used.getOrDefault(section, 0) >= cap) {
                continue;
            }
            keep[i] = true;
            kept++;
            used.merge(section, 1, Integer::sum);
        }
        for (int i = 0; i < ranked.size() && kept < topK; i++) {
            if (!keep[i]) {
                keep[i] = true;
                kept++;
            }
        }
        List<Candidate> result = new ArrayList<>(kept);
        for (int i = 0; i < ranked.size(); i++) {
            if (keep[i]) {
                result.add(ranked.get(i));
            }
        }
        return result;
    }

    /** 候选所属标段（未绑标段归到空串这一组） */
    private static String sectionOf(Candidate candidate) {
        String section = asString(candidate.document.getMetadata().get(MD_SECTION));
        return section == null ? "" : section.trim();
    }

    /**
     * 关键词通道粗召回（BM25 / MySQL FULLTEXT + ngram）。
     *
     * <p>开关关闭、查询为空、或数据库报错（例如索引尚未迁移）时返回空表并记 WARN——
     * 关键词通道是"补充通道"，任何异常都不允许把整条召回链打挂，也不允许改变 hit 口径。
     */
private List<BidAgentChunkDO> searchByKeyword(BidRetrieveReqDTO reqDTO) {
    return searchByKeyword(reqDTO, ObjUtil.defaultIfNull(properties.getRetrieve().getBm25TopK(), 8));
}

private List<BidAgentChunkDO> searchByKeyword(BidRetrieveReqDTO reqDTO, int limit) {
    return searchByKeyword(reqDTO, BidTextCleaner.normalizeEncoding(reqDTO.getQuery()), limit);
}

private List<BidAgentChunkDO> searchByKeyword(BidRetrieveReqDTO reqDTO, String query) {
    int limit = ObjUtil.defaultIfNull(properties.getRetrieve().getBm25TopK(), 8);
    return searchByKeyword(reqDTO, query, limit);
}

private List<BidAgentChunkDO> searchByKeyword(BidRetrieveReqDTO reqDTO, String query, int limit) {
        BidAgentProperties.Retrieve config = properties.getRetrieve();
        if (config == null || !Boolean.TRUE.equals(config.getBm25Enabled()) || chunkMapper == null) {
            return List.of();
        }
        if (limit <= 0) {
            return List.of();
        }
        BidRetrieveReqDTO.Filters filters = reqDTO.getFilters();
        try {
// projectId=0 与 null 同为「无需选项目的一键生成」：向量侧 filterByScope 对 0 跳过项目过滤，
// 关键词通道此处对齐同一口径，否则 0 会把 project_id 为 NULL 的切片全部滤掉（两通道结果短路）。
Long projectId = reqDTO.getProjectId() != null && reqDTO.getProjectId() != 0L
        ? reqDTO.getProjectId() : null;
List<BidAgentChunkDO> rows = chunkMapper.selectByKeyword(query, projectId,
                    filters == null ? null : filters.getDocType(),
                    sectionsOf(filters),
                    sectionIncludeUnbound(),
                    filters == null ? null : filters.getSourceDocName(),
                    limit);
            return rows == null ? List.of() : rows;
        } catch (Exception ex) {
            // 索引缺失/方言不匹配等只降级为"无关键词候选"，与 bm25-enabled=false 等价
            log.warn("[retrieve][关键词通道异常，按无关键词候选继续] query={} msg={}", query, ex.getMessage());
            return List.of();
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合向量与关键词两路候选：
     * 每个候选得分 = Σ 1/(k + rank)，rank 从 1 开始。用排名而非原始分，规避余弦/BM25/rerank 三种量纲不可比的问题。
     *
     * <p>去重键：向量候选用 Qdrant 点 id（= 切片表 vector_id），关键词候选优先用 vector_id，
     * 缺失时退化为 sourceBlockIds、再退化为内容哈希。
     */
    private List<Candidate> fuseCandidates(List<Document> documents, List<BidAgentChunkDO> keywordRows, int rrfK) {
        Map<String, Candidate> merged = new LinkedHashMap<>();
        int rank = 0;
        for (Document doc : documents) {
            rank += 1;
            String key = candidateKey(doc.getId(), asString(doc.getMetadata().get(MD_BLOCK_IDS)), doc.getText());
            Candidate candidate = merged.computeIfAbsent(key, ignored -> new Candidate(doc));
            candidate.channels.add(CHANNEL_VECTOR);
            candidate.vectorScore = doc.getScore();
            candidate.rrf += 1.0d / (rrfK + rank);
        }
        rank = 0;
        for (BidAgentChunkDO row : keywordRows) {
            rank += 1;
            String key = candidateKey(row.getVectorId(), row.getSourceBlockIds(), row.getContent());
            Candidate candidate = merged.get(key);
            if (candidate == null) {
                candidate = new Candidate(toDocument(row));
                merged.put(key, candidate);
            }
            candidate.channels.add(CHANNEL_KEYWORD);
            candidate.keywordScore = row.getKeywordScore();
            if (row.getKeywordScore() != null) {
                candidate.document.getMetadata().put("_keywordScore", row.getKeywordScore());
            }
            candidate.rrf += 1.0d / (rrfK + rank);
        }
        List<Candidate> candidates = new ArrayList<>(merged.values());
        candidates.sort(Comparator.comparingDouble((Candidate item) -> item.rrf).reversed());
        return candidates;
    }

    /**
     * 关键词候选（切片表行）→ Spring AI Document：metadata 字段与入库侧保持同一套键，溯源信息不丢。
     *
     * <p>注意：Spring AI 的 Document 构造函数**不接受 metadata 的 null 值**（会抛
     * "metadata cannot have null values"），而切片行的 chapter_path / page_no 等列本来就可空，
     * 故这里只放非空字段——缺失键在 filterByScope / toChunk 侧按 null 处理，语义一致。
     */
    private Document toDocument(BidAgentChunkDO row) {
        Map<String, Object> metadata = new HashMap<>();
        putIfNotNull(metadata, MD_TENANT_ID, row.getTenantId());
        putIfNotNull(metadata, MD_PROJECT_ID, row.getProjectId());
        putIfNotNull(metadata, MD_DOC_TYPE, row.getDocType());
        putIfNotNull(metadata, MD_SECTION, row.getBidSectionCode());
        putIfNotNull(metadata, MD_SOURCE_DOC, row.getSourceDocName());
        putIfNotNull(metadata, MD_CHAPTER, row.getChapterPath());
        putIfNotNull(metadata, MD_PAGE, row.getPageNo());
        putIfNotNull(metadata, MD_BLOCK_IDS, row.getSourceBlockIds());
        putIfNotNull(metadata, "_keywordScore", row.getKeywordScore());
        String id = StrUtil.isNotBlank(row.getVectorId()) ? row.getVectorId() : candidateKey(null, row.getSourceBlockIds(), row.getContent());
        return new Document(id, StrUtil.nullToEmpty(row.getContent()), metadata);
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String candidateKey(String vectorId, String sourceBlockIds, String content) {
        if (StrUtil.isNotBlank(vectorId)) {
            return "v:" + vectorId;
        }
        if (StrUtil.isNotBlank(sourceBlockIds)) {
            return "b:" + sourceBlockIds;
        }
        return "c:" + StrUtil.nullToEmpty(content).hashCode();
    }

    /**
     * 未启用 rerank 时的排序与闸门：按 RRF 融合分降序返回**过阈值**的候选（不截断 topK，交给 selectTopK）。
     *
     * <p>阈值只约束**向量分数**（min-score 的语义是余弦相似度阈值）；关键词通道没有余弦分，
     * 不能拿向量阈值去卡它，否则"仅关键词召回命中的片段"会被误杀。
     */
    private List<Candidate> rankByRrf(List<Candidate> candidates, double minScore) {
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.vectorScore != null && candidate.vectorScore < minScore) {
                continue;
            }
            ranked.add(candidate);
        }
        return ranked;
    }

    /** Rerank 重排融合后的候选：按 rerank 分数降序返回过阈值候选，并把分数写回 metadata 供 toChunk 取值 */
    private List<Candidate> rankByRerank(String query, List<Candidate> candidates, double minScore) {
        List<String> texts = new ArrayList<>();
        for (Candidate candidate : candidates) {
            texts.add(candidate.document.getText());
        }
        List<DashScopeRerankClient.RerankResult> results = rerankClient.rerank(query, texts);
        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (DashScopeRerankClient.RerankResult result : results) {
            scoreByIndex.put(result.getIndex(), result.getScore());
        }
        List<Integer> order = scoreByIndex.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= minScore)
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        List<Candidate> ranked = new ArrayList<>(order.size());
        for (int idx : order) {
// 远端 rerank 返回的下标不信任：越界时跳过并留痕，避免 IndexOutOfBoundsException 打穿契约端降级
if (idx < 0 || idx >= candidates.size()) {
    log.warn("[rerankAndFilter][远端 rerank 返回越界下标，已跳过] idx={} size={}", idx, candidates.size());
    continue;
}
            Candidate candidate = candidates.get(idx);
            candidate.rerankScore = scoreByIndex.get(idx);
            candidate.document.getMetadata().put("_rerankScore", candidate.rerankScore);
            ranked.add(candidate);
        }
        return ranked;
    }

    private BidRetrieveRespDTO.Chunk toChunk(Candidate candidate, boolean reranked) {
        Document doc = candidate.document;
        Map<String, Object> md = doc.getMetadata();
        BidRetrieveRespDTO.Chunk chunk = new BidRetrieveRespDTO.Chunk();
        chunk.setChunkId(doc.getId());
        chunk.setContent(doc.getText());
        chunk.setSourceDocName(asString(md.get(MD_SOURCE_DOC)));
        chunk.setBidSectionCode(asString(md.get(MD_SECTION)));
        chunk.setChapterPath(asString(md.get(MD_CHAPTER)));
        chunk.setPageNo(asInteger(md.get(MD_PAGE)));
        chunk.setSourceBlockIds(split(asString(md.get(MD_BLOCK_IDS))));
        // 分数口径：rerank 命中给 rerank 分；否则给向量余弦分（关键词独占候选没有余弦分，保持 null）
        if (reranked && candidate.rerankScore != null) {
            chunk.setScore(candidate.rerankScore);
        } else if (candidate.vectorScore != null) {
            chunk.setScore(candidate.vectorScore);
        } else {
            chunk.setScore(normalizeKeywordScore(candidate.keywordScore));
        }
        chunk.setMatchedBy(matchedBy(candidate, reranked));
        return chunk;
    }

    /**
     * 命中方式（契约给出参字符串，前端直接展示）：
     * rerank（经过重排）> hybrid（向量 + 关键词双通道召回）> bm25（仅关键词）> vector（仅向量）。
     */
    private static String matchedBy(Candidate candidate, boolean reranked) {
        if (reranked) {
            return "rerank";
        }
        if (candidate.channels.size() > 1) {
            return "hybrid";
        }
        return candidate.channels.contains(CHANNEL_KEYWORD) ? "bm25" : "vector";
    }

    /** 融合候选：同一个片段可能同时来自向量与关键词通道，用 channels 记住来源以便回填 matchedBy */
    /** BM25 原始全文相关度 -> 0~1 单调归一化分；仅用于统一展示，不代表余弦相似度。 */
    private static Double normalizeKeywordScore(Double rawScore) {
        if (rawScore == null || rawScore <= 0) {
            return null;
        }
        return rawScore / (1.0d + rawScore);
    }
    private static final class Candidate {

        private final Document document;

        private final Set<String> channels = new LinkedHashSet<>();

        private double rrf;

        private Double vectorScore;
        private Double keywordScore;

        private Double rerankScore;

        private Candidate(Document document) {
            this.document = document;
        }
    }

    private List<String> checkMandatory(List<String> mandatory, List<BidRetrieveRespDTO.Chunk> chunks) {
        if (CollUtil.isEmpty(mandatory)) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String keyword : mandatory) {
            boolean hit = chunks.stream().anyMatch(
                    chunk -> chunk.getContent() != null && chunk.getContent().contains(keyword));
            if (!hit) {
                missing.add(keyword);
            }
        }
        return missing;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 溯源块号拆分：逐项 trim 并去掉空项（元数据里可能是 "a, b"，带空格会让块号比对与前端展示失真） */
    private static List<String> split(String joined) {
        if (StrUtil.isBlank(joined)) {
            return List.of();
        }
        List<String> blockIds = new ArrayList<>();
        for (String part : joined.split(",")) {
            String blockId = part.trim();
            if (!blockId.isEmpty()) {
                blockIds.add(blockId);
            }
        }
        return blockIds;
    }
}
