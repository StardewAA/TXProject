package cn.iocoder.txgy.module.cms.service.law;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import cn.iocoder.txgy.module.cms.service.search.LegalIdentifierNormalizer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_ARTICLE_NOT_FOUND;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** Elasticsearch 8.x 法规关键词检索实现。Redis 向量结果将在嵌入模型接入后于本服务中融合。 */
@Service
@Slf4j
public class CmsLawSearchServiceImpl implements CmsLawApi {

    private final ObjectProvider<ElasticsearchClientProvider> clientProvider;
    private final ElasticsearchSearchProperties searchProperties;

    public CmsLawSearchServiceImpl(ObjectProvider<ElasticsearchClientProvider> clientProvider,
                                   ElasticsearchSearchProperties searchProperties) {
        this.clientProvider = clientProvider;
        this.searchProperties = searchProperties;
    }

    @Override
    public PageResult<CmsLawSearchRespDTO> search(CmsLawSearchReqDTO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ElasticsearchClient client = requiredClient();
        int from = (request.getPageNo() - 1) * request.getPageSize();
        try {
            SearchResponse<Map> response = client.search(builder -> builder
                            .index(readAlias())
                            .from(from)
                            .size(request.getPageSize())
                            .trackTotalHits(track -> track.enabled(true))
                            .query(buildSearchQuery(request, tenantId)),
                    Map.class);
            List<CmsLawSearchRespDTO> list = response.hits().hits().stream()
                    .map(CmsLawSearchServiceImpl::toResponse)
                    .toList();
            long total = response.hits().total() == null ? list.size() : response.hits().total().value();
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
                            .index(readAlias())
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

    static Query buildSearchQuery(CmsLawSearchReqDTO request, Long tenantId) {
        String keyword = request.getKeyword().trim();
        String normalized = LegalIdentifierNormalizer.normalize(keyword);

        List<Query> shouldQueries = new ArrayList<>();
        shouldQueries.add(MultiMatchQuery.of(match -> match
                .query(keyword)
                .fields("title^5", "document_no^8", "article_no^8", "content^2", "issuing_authority")
                .minimumShouldMatch("1"))
                ._toQuery());
        if (normalized != null) {
            shouldQueries.add(TermQuery.of(term -> term
                    .field("document_no_normalized")
                    .value(normalized)
                    .boost(30.0f))._toQuery());
            shouldQueries.add(TermQuery.of(term -> term
                    .field("article_no_normalized")
                    .value(normalized)
                    .boost(30.0f))._toQuery());
        }

        BoolQuery.Builder bool = new BoolQuery.Builder()
                .should(shouldQueries)
                .minimumShouldMatch("1")
                .filter(term("tenant_id", tenantId.toString()))
                .filter(term("published", true));
        addOptionalTermFilter(bool, "industry_code", request.getIndustryCode());
        addOptionalTermFilter(bool, "region_code", request.getRegionCode());
        addOptionalTermFilter(bool, "law_type", request.getLawType());
        if (request.getStatus() != null) {
            bool.filter(term("status", request.getStatus().name()));
        }
        if (request.getAsOfDate() != null) {
            addAsOfDateFilters(bool, request.getAsOfDate());
        }
        return bool.build()._toQuery();
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
        result.setTitle(stringValue(source.get("title")));
        result.setDocumentNo(stringValue(source.get("document_no")));
        result.setArticleNo(stringValue(source.get("article_no")));
        result.setContent(stringValue(source.get("content")));
        result.setVersion(stringValue(source.get("version")));
        result.setIssuingAuthority(stringValue(source.get("issuing_authority")));
        result.setSourceUrl(stringValue(source.get("source_url")));
        result.setEffectiveDate(dateValue(source.get("effective_date")));
        result.setExpiryDate(dateValue(source.get("expiry_date")));
        result.setStatus(statusValue(source.get("status")));
        result.setScore(hit.score());
        return result;
    }

    /** 按 sourceId 回读融合命中，避免把 Redis 当作法规原文存储。 */
    public Map<String, CmsLawSearchRespDTO> findPublishedBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Map.of();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            List<FieldValue> values = sourceIds.stream().distinct().map(FieldValue::of).toList();
            Query query = new BoolQuery.Builder()
                    .filter(term("tenant_id", tenantId.toString()))
                    .filter(term("published", true))
                    .filter(filter -> filter.terms(terms -> terms.field("source_id")
                            .terms(field -> field.value(values))))
                    .build()._toQuery();
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(readAlias())
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
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(readAlias())
                            .size(limit)
                            .query(query -> query.bool(bool -> bool
                                    .filter(term("tenant_id", tenantId.toString()))
                                    .filter(term("published", true)))),
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

    private String readAlias() {
        return searchProperties.getIndexPrefix() + "-law-read";
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
