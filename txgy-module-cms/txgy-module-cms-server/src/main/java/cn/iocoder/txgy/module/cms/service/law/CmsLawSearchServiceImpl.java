package cn.iocoder.txgy.module.cms.service.law;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSuggestionDTO;
import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import cn.iocoder.txgy.module.cms.service.search.LegalIdentifierNormalizer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.PrefixQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_ARTICLE_NOT_FOUND;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** Elasticsearch 8.x 法规关键词检索实现。Redis 向量结果将在嵌入模型接入后于本服务中融合。 */
@Service
@Slf4j
public class CmsLawSearchServiceImpl implements CmsLawApi {

    private static final String DOCUMENT_COUNT_AGGREGATION = "document_count";
    private static final int SEARCH_FRAGMENT_SIZE = 260;
    private static final Pattern STANDARD_IDENTIFIER_PATTERN = Pattern.compile(
            "(?i)(?:GB(?:\\s*/\\s*[TZ]|\\s*T)?|GJB|ISO|IEC|HG\\s*/\\s*T|SY\\s*/\\s*T|CJ\\s*/\\s*T|CJJ|JGJ|NB\\s*/\\s*T|DL\\s*/\\s*T)"
                    + "\\s*[A-Z]*\\s*\\d+(?:\\.\\d+)*(?:\\s*[-—–]\\s*\\d{2,4})?");
    private static final Pattern ARTICLE_IDENTIFIER_PATTERN = Pattern.compile(
            "第\\s*[0-9一二三四五六七八九十百千万零〇两.]+\\s*条");

    private final ObjectProvider<ElasticsearchClientProvider> clientProvider;
    private final ElasticsearchSearchProperties searchProperties;

    public CmsLawSearchServiceImpl(ObjectProvider<ElasticsearchClientProvider> clientProvider,
                                   ElasticsearchSearchProperties searchProperties) {
        this.clientProvider = clientProvider;
        this.searchProperties = searchProperties;
    }

    @Override
    public PageResult<CmsLawSearchRespDTO> search(CmsLawSearchReqDTO request) {
        return searchByCategory(request, request.getDocCategory());
    }

    /** 按知识库类型收紧关键词召回；null 表示综合检索。 */
    public PageResult<CmsLawSearchRespDTO> searchByCategory(CmsLawSearchReqDTO request, String docCategory) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ElasticsearchClient client = requiredClient();
        int from = (request.getPageNo() - 1) * request.getPageSize();
        try {
            SearchResponse<Map> response = client.search(builder -> builder
                            .index(readAliases(docCategory))
                            .from(from)
                            .size(request.getPageSize())
                            .trackTotalHits(track -> track.enabled(true))
                            .query(buildSearchQuery(request, tenantId, docCategory))
                            // 一个法规/标准可能有数十个切片。搜索列表只展示每份文档得分最高的切片，
                            // 避免标准号或标题命中后把整份原文的全部切片铺满页面。
                            .collapse(collapse -> collapse.field("title.raw"))
                            .aggregations(DOCUMENT_COUNT_AGGREGATION,
                                    aggregation -> aggregation.cardinality(cardinality -> cardinality.field("title.raw")))
                            .highlight(highlight -> highlight
                                    .fields("content", field -> field
                                            .fragmentSize(SEARCH_FRAGMENT_SIZE)
                                            .numberOfFragments(1))),
                    Map.class);
            List<CmsLawSearchRespDTO> list = response.hits().hits().stream()
                    .map(CmsLawSearchServiceImpl::toSearchResponse)
                    .toList();
            long total = distinctDocumentCount(response, list.size());
            return new PageResult<>(list, total);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            log.error("[search][法规检索失败，tenantId={}, keyword={}]", tenantId, request.getKeyword(), ex);
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    @Override
    public CmsLawSearchRespDTO getArticle(Long articleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(readAliases(null))
                            .size(1)
                            .query(query -> query.bool(bool -> bool
                                    .filter(term("tenant_id", tenantId.toString()))
                                    .filter(term("article_id", articleId))
                                    .filter(term("published", true)))),
                    Map.class);
            if (response.hits().hits().isEmpty()) {
                throw exception(LAW_ARTICLE_NOT_FOUND);
            }
            return toResponse(response.hits().hits().getFirst());
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            log.error("[getArticle][法规条款读取失败，tenantId={}, articleId={}]", tenantId, articleId, ex);
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    @Override
    public List<CmsLawSuggestionDTO> suggest(String keyword,int limit) {
        Long tenantId=TenantContextHolder.getRequiredTenantId();
        String value=keyword==null?"":keyword.strip();
        if(value.isEmpty()) return List.of();
        try {
            SearchResponse<Map> response=requiredClient().search(builder->builder.index(readAliases(null))
                    .size(Math.min(50,Math.max(5,limit*5)))
                    .source(source->source.filter(filter->filter.includes("title","document_no","issuing_authority","law_type")))
                    .query(query->query.bool(bool->bool
                            .filter(term("tenant_id",tenantId.toString()))
                            .filter(term("published",true))
                            .must(must->must.multiMatch(match->match.query(value)
                                    .fields("title^5","document_no^8","issuing_authority^2","content"))))),Map.class);
            LinkedHashSet<String> seen=new LinkedHashSet<>();
            List<CmsLawSuggestionDTO> result=new ArrayList<>();
            for(Hit<Map> hit:response.hits().hits()) {
                Map source=hit.source();
                if(source==null) continue;
                String title=stringValue(source.get("title"));
                if(title==null || !seen.add(title)) continue;
                CmsLawSuggestionDTO item=new CmsLawSuggestionDTO();
                item.setTitle(title);
                item.setDocumentNo(stringValue(source.get("document_no")));
                item.setIssuingAuthority(stringValue(source.get("issuing_authority")));
                item.setLawType(stringValue(source.get("law_type")));
                result.add(item);
                if(result.size()>=Math.max(1,Math.min(limit,5))) break;
            }
            return List.copyOf(result);
        } catch(IOException | RuntimeException ex) {
            log.warn("[suggest][法规联想失败，tenantId={}, keyword={}]",tenantId,value,ex);
            return List.of();
        }
    }

    static Query buildSearchQuery(CmsLawSearchReqDTO request, Long tenantId) {
        return buildSearchQuery(request, tenantId, null);
    }

    static Query buildSearchQuery(CmsLawSearchReqDTO request, Long tenantId, String docCategory) {
        String keyword = request.getKeyword().trim();
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(term("tenant_id", tenantId.toString()))
                .filter(term("published", true));

        String identifier = extractIdentifier(keyword);
        if (identifier != null) {
            String normalized = LegalIdentifierNormalizer.normalize(identifier);
            // 标准号/条款号属于确定性标识，必须命中完整前缀；不能因为公共词 GB/T
            // 命中就召回其他标准。允许省略年份，例如 GB/T 23257 可命中 2017 版本。
            bool.must(query -> query.bool(identifierQuery -> identifierQuery
                    .should(PrefixQuery.of(prefix -> prefix.field("document_no_normalized")
                            .value(normalized).boost(40.0f))._toQuery())
                    .should(PrefixQuery.of(prefix -> prefix.field("article_no_normalized")
                            .value(normalized).boost(40.0f))._toQuery())
                    .minimumShouldMatch("1")));
            bool.should(strictFullTextQuery(keyword));
        } else if (looksLikeDocumentTitle(keyword)) {
            // 输入“安全生产法”“消防法”等文档名称时，优先整词短语匹配标题；
            // 短语不命中时退回分词 AND 匹配，避免“生态法”这类简称/部分名称搜不到。
            bool.should(query -> query.matchPhrase(phrase -> phrase.field("title")
                    .query(keyword).boost(12.0f)));
            // 标题分词全命中（如“生态法”命中“生态环境法典”），权重高于正文兜底
            bool.should(query -> query.match(match -> match.field("title")
                    .query(keyword).operator(Operator.And).boost(20.0f)));
            bool.should(strictFullTextQuery(keyword));
            bool.minimumShouldMatch("1");
        } else if (looksLikeQuestion(keyword)) {
            // 自然语言提问（AI 问答场景）改用 OR + 最少命中 60%，否则整句分词后无法召回。
            bool.must(relaxedFullTextQuery(keyword));
        } else {
            // 普通关键词要求所有分词共同命中同一字段，避免“任意一个字命中”造成整库召回。
            bool.must(strictFullTextQuery(keyword));
            bool.should(query -> query.matchPhrase(phrase -> phrase.field("title")
                    .query(keyword).boost(8.0f)));
            bool.should(query -> query.matchPhrase(phrase -> phrase.field("content")
                    .query(keyword).boost(4.0f)));
        }
        addOptionalTermFilter(bool, "doc_category", docCategory);
        boolean financePolicy = "FINANCE_POLICY".equalsIgnoreCase(docCategory);
        addOptionalTermFilter(bool, financePolicy ? "applicable_industries" : "industry_code",
                request.getIndustryCode());
        addOptionalTermFilter(bool, financePolicy ? "applicable_regions" : "region_code",
                request.getRegionCode());
        addOptionalTermFilter(bool, "law_type", request.getLawType());
        if(request.getIssuingAuthority()!=null && !request.getIssuingAuthority().isBlank()) {
            bool.filter(query->query.matchPhrase(match->match.field("issuing_authority")
                    .query(request.getIssuingAuthority().strip())));
        }
        if (request.getStatus() != null) {
            bool.filter(term("status", request.getStatus().name()));
        }
        if (request.getAsOfDate() != null) {
            addAsOfDateFilters(bool, request.getAsOfDate());
        }
        if(request.getEffectiveDateFrom()!=null || request.getEffectiveDateTo()!=null) {
            bool.filter(query->query.range(range->range.date(dateRange->{
                dateRange.field("effective_date");
                if(request.getEffectiveDateFrom()!=null) dateRange.gte(request.getEffectiveDateFrom().toString());
                if(request.getEffectiveDateTo()!=null) dateRange.lte(request.getEffectiveDateTo().toString());
                return dateRange;
            })));
        }
        if (financePolicy) addFinancePolicyFilters(bool, request);
        return bool.build()._toQuery();
    }

    private static void addFinancePolicyFilters(BoolQuery.Builder bool, CmsLawSearchReqDTO request) {
        if (request.getPolicyStatus() != null) {
            bool.filter(term("policy_status", request.getPolicyStatus().longValue()));
        }
        if (request.getAmountMin() != null) {
            bool.filter(query -> query.range(range -> range.number(number -> number.field("amount_max")
                    .gte(request.getAmountMin().doubleValue()))));
        }
        if (request.getAmountMax() != null) {
            bool.filter(query -> query.range(range -> range.number(number -> number.field("amount_min")
                    .lte(request.getAmountMax().doubleValue()))));
        }
        if (request.getApplyDeadlineFrom() != null || request.getApplyDeadlineTo() != null) {
            bool.filter(query -> query.range(range -> range.date(date -> {
                date.field("apply_end_time");
                if (request.getApplyDeadlineFrom() != null) date.gte(request.getApplyDeadlineFrom().toString());
                if (request.getApplyDeadlineTo() != null) date.lte(request.getApplyDeadlineTo() + "T23:59:59");
                return date;
            })));
        }
    }

    private static Query strictFullTextQuery(String keyword) {
        return MultiMatchQuery.of(match -> match
                .query(keyword)
                .fields("title^5", "document_no^8", "article_no^8", "content^2", "issuing_authority")
                .operator(Operator.And))
                ._toQuery();
    }

    /** 自然语言问句判定：长度较长或包含疑问词时按“问题”检索，而不是精确关键词。 */
    static boolean looksLikeQuestion(String keyword) {
        String value = keyword == null ? "" : keyword.strip();
        if (value.isEmpty()) {
            return false;
        }
        if (value.length() > 12) {
            return true;
        }
        return value.contains("？") || value.contains("?") || value.contains("如何")
                || value.contains("怎么") || value.contains("哪些") || value.contains("什么")
                || value.contains("是否") || value.contains("为什么");
    }

    private static Query relaxedFullTextQuery(String keyword) {
        return MultiMatchQuery.of(match -> match
                .query(keyword)
                .fields("title^5", "document_no^8", "article_no^8", "content^2", "issuing_authority")
                .operator(Operator.Or)
                .minimumShouldMatch("60%"))
                ._toQuery();
    }

    static String extractIdentifier(String keyword) {
        String searchable = Normalizer.normalize(keyword, Normalizer.Form.NFKC);
        Matcher standard = STANDARD_IDENTIFIER_PATTERN.matcher(searchable);
        if (standard.find()) return standard.group();
        Matcher article = ARTICLE_IDENTIFIER_PATTERN.matcher(searchable);
        return article.find() ? article.group() : null;
    }

    static boolean looksLikeDocumentTitle(String keyword) {
        String value = keyword == null ? "" : keyword.strip();
        if (value.isEmpty() || value.length() > 60 || value.matches(".*\\s+.*")) return false;
        return value.endsWith("法") || value.endsWith("法典") || value.endsWith("条例")
                || value.endsWith("规定") || value.endsWith("办法") || value.endsWith("方案")
                || value.endsWith("标准") || value.endsWith("规范");
    }

    private static void addOptionalTermFilter(BoolQuery.Builder bool, String field, String value) {
        if (value != null && !value.isBlank()) {
            bool.filter(term(field, value.trim()));
        }
    }

    private static void addAsOfDateFilters(BoolQuery.Builder bool, LocalDate asOfDate) {
        String date = asOfDate.toString();
        bool.filter(query -> query.bool(valid -> valid
                .should(queryBuilder -> queryBuilder.bool(missing -> missing
                        .mustNot(not -> not.exists(exists -> exists.field("effective_date")))))
                .should(queryBuilder -> queryBuilder.range(range -> range
                        .date(dateRange -> dateRange.field("effective_date").lte(date))))
                .minimumShouldMatch("1")));
        bool.filter(query -> query.bool(valid -> valid
                .should(queryBuilder -> queryBuilder.bool(missing -> missing
                        .mustNot(not -> not.exists(exists -> exists.field("expiry_date")))))
                .should(queryBuilder -> queryBuilder.range(range -> range
                        .date(dateRange -> dateRange.field("expiry_date").gte(date))))
                .minimumShouldMatch("1")));
    }

    private static Query term(String field, String value) {
        return TermQuery.of(term -> term.field(field).value(value))._toQuery();
    }

    private static Query term(String field, Long value) {
        return TermQuery.of(term -> term.field(field).value(value))._toQuery();
    }

    private static Query term(String field, boolean value) {
        return TermQuery.of(term -> term.field(field).value(value))._toQuery();
    }

    static CmsLawSearchRespDTO toResponse(Hit<Map> hit) {
        Map source = hit.source();
        if (source == null) {
            source = Map.of();
        }
        CmsLawSearchRespDTO result = new CmsLawSearchRespDTO();
        result.setLawId(longValue(source.get("law_id")));
        result.setArticleId(longValue(source.get("article_id")));
        result.setSourceId(stringValue(source.get("source_id")));
        result.setReviewId(stringValue(source.get("review_id")));
        result.setDocCategory(stringValue(source.get("doc_category")));
        result.setTitle(stringValue(source.get("title")));
        result.setDocumentNo(stringValue(source.get("document_no")));
        result.setArticleNo(stringValue(source.get("article_no")));
        result.setContent(stringValue(source.get("content")));
        result.setVersion(stringValue(source.get("version")));
        result.setIssuingAuthority(stringValue(source.get("issuing_authority")));
        result.setSourceUrl(stringValue(source.get("source_url")));
        result.setLocalOriginalAvailable(booleanValue(source.get("has_local_original")));
        result.setPageNumber(integerValue(source.get("page_number")));
        result.setEffectiveDate(dateValue(source.get("effective_date")));
        result.setExpiryDate(dateValue(source.get("expiry_date")));
        result.setStatus(statusValue(source.get("status")));
        result.setScore(hit.score());
        return result;
    }

    static CmsLawSearchRespDTO toSearchResponse(Hit<Map> hit) {
        CmsLawSearchRespDTO result = toResponse(hit);
        List<String> fragments = hit.highlight().get("content");
        String content = fragments == null || fragments.isEmpty()
                ? result.getContent() : fragments.getFirst();
        result.setContent(compactSnippet(content, SEARCH_FRAGMENT_SIZE));
        return result;
    }

    private static String compactSnippet(String content, int maximumLength) {
        if (content == null) return null;
        String compact = content
                .replace("<em>", "")
                .replace("</em>", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (compact.length() <= maximumLength) return compact;
        return compact.substring(0, maximumLength).stripTrailing() + "…";
    }

    private static long distinctDocumentCount(SearchResponse<Map> response, int fallback) {
        if (response.aggregations() == null) return fallback;
        var aggregate = response.aggregations().get(DOCUMENT_COUNT_AGGREGATION);
        if (aggregate == null || !aggregate.isCardinality()) return fallback;
        return (long) aggregate.cardinality().value();
    }

    /** 按 sourceId 回读融合命中，避免把 Redis 当作法规原文存储。 */
    public Map<String, CmsLawSearchRespDTO> findPublishedBySourceIds(List<String> sourceIds) {
        return findPublishedBySourceIds(sourceIds, null);
    }

    public Map<String, CmsLawSearchRespDTO> findPublishedBySourceIds(List<String> sourceIds, String docCategory) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Map.of();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            List<FieldValue> values = sourceIds.stream().distinct().map(FieldValue::of).toList();
            BoolQuery.Builder queryBuilder = new BoolQuery.Builder()
                    .filter(term("tenant_id", tenantId.toString()))
                    .filter(term("published", true))
                    .filter(filter -> filter.terms(terms -> terms.field("source_id")
                            .terms(field -> field.value(values))));
            addOptionalTermFilter(queryBuilder, "doc_category", docCategory);
            Query query = queryBuilder.build()._toQuery();
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(readAliases(docCategory))
                            .size(values.size())
                            .query(query),
                    Map.class);
            Map<String, CmsLawSearchRespDTO> result = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                CmsLawSearchRespDTO item = toResponse(hit);
                result.put(item.getSourceId(), item);
            }
            return result;
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    /** 读取当前租户全部已发布片段，用于受控的向量索引重建。 */
    public List<CmsLawSearchRespDTO> listPublishedForVectorIndex(int limit) {
        return listPublishedForVectorIndex("QA", limit);
    }

    public List<CmsLawSearchRespDTO> listPublishedForVectorIndex(String docCategory, int limit) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(readAliases(docCategory))
                            .size(limit)
                            .query(query -> query.bool(bool -> bool
                                    .filter(term("tenant_id", tenantId.toString()))
                                    .filter(term("published", true))
                                    .filter(term("doc_category", docCategory)))),
                    Map.class);
            return response.hits().hits().stream().map(CmsLawSearchServiceImpl::toResponse).toList();
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private ElasticsearchClient requiredClient() {
        ElasticsearchClientProvider provider = clientProvider.getIfAvailable();
        if (provider == null) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
        return provider.client();
    }

    private List<String> readAliases(String docCategory) {
        if ("QA".equalsIgnoreCase(docCategory)) {
            return List.of(searchProperties.getIndexPrefix() + "-qa-read");
        }
        if ("LAW".equalsIgnoreCase(docCategory)) {
            return List.of(searchProperties.getIndexPrefix() + "-law-read");
        }
        if ("FINANCE_POLICY".equalsIgnoreCase(docCategory)) {
            return List.of(searchProperties.getIndexPrefix() + "-finance-policy-read");
        }
        return List.of(searchProperties.getIndexPrefix() + "-law-read",
                searchProperties.getIndexPrefix() + "-qa-read",
                searchProperties.getIndexPrefix() + "-finance-policy-read");
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = stringValue(value);
        return text == null || text.isBlank() ? null : Long.valueOf(text);
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        String text = stringValue(value);
        return text == null || text.isBlank() ? null : Integer.valueOf(text);
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) return flag;
        String text = stringValue(value);
        return text == null || text.isBlank() ? null : Boolean.valueOf(text);
    }

    private static LocalDate dateValue(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            log.warn("[dateValue][忽略无法解析的 Elasticsearch 日期：{}]", text);
            return null;
        }
    }

    private static CmsLawStatusEnum statusValue(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return CmsLawStatusEnum.UNKNOWN;
        }
        try {
            return CmsLawStatusEnum.valueOf(text);
        } catch (IllegalArgumentException ex) {
            return CmsLawStatusEnum.UNKNOWN;
        }
    }

}
