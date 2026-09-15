package cn.iocoder.txgy.module.cms.service.law;

import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.DefaultElasticsearchClientProvider;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import co.elastic.clients.elasticsearch._types.Refresh;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "txgy.search.elasticsearch.integration", matches = "true")
class CmsLawSearchServiceIntegrationTest {

    private static final String DOCUMENT_ID = "cms-law-search-integration-test";
    private static final Long TENANT_ID = 987654321L;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSearchExactDocumentNumberAgainstRealElasticsearch() throws Exception {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setEnabled(true);
        try (DefaultElasticsearchClientProvider provider = new DefaultElasticsearchClientProvider(properties)) {
            provider.client().index(request -> request
                    .index("txgy-cms-law-write")
                    .id(DOCUMENT_ID)
                    .refresh(Refresh.True)
                    .document(document()));
            try {
                StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
                beanFactory.addBean("elasticsearchClientProvider", provider);
                CmsLawSearchServiceImpl service = new CmsLawSearchServiceImpl(
                        beanFactory.getBeanProvider(ElasticsearchClientProvider.class), properties);
                TenantContextHolder.setTenantId(TENANT_ID);
                CmsLawSearchReqDTO request = new CmsLawSearchReqDTO();
                request.setKeyword("ＧＢ ５０２６４－２０１３");

                var page = service.search(request);

                assertThat(page.getTotal()).isPositive();
                assertThat(page.getList()).extracting(CmsLawSearchRespDTO::getArticleId).contains(900002L);
            } finally {
                provider.client().delete(request -> request
                        .index("txgy-cms-law-write")
                        .id(DOCUMENT_ID)
                        .refresh(Refresh.True));
            }
        }
    }

    private static Map<String, Object> document() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tenant_id", TENANT_ID.toString());
        source.put("law_id", 900001L);
        source.put("article_id", 900002L);
        source.put("source_id", "integration-test-source");
        source.put("version", "2013");
        source.put("title", "工业设备及管道绝热工程设计规范");
        source.put("document_no", "GB 50264-2013");
        source.put("document_no_normalized", "GB502642013");
        source.put("article_no", "第5.1.2条");
        source.put("article_no_normalized", "第5.1.2条");
        source.put("content", "工业设备及管道绝热工程应符合设计要求。");
        source.put("issuing_authority", "住房和城乡建设部");
        source.put("industry_code", "INDUSTRIAL");
        source.put("region_code", "CN");
        source.put("law_type", "STANDARD");
        source.put("status", "EFFECTIVE");
        source.put("effective_date", "2013-10-01");
        source.put("source_url", "https://example.test/gb50264");
        source.put("published", true);
        source.put("revision", 1L);
        source.put("updated_at", "2026-09-04");
        return source;
    }

}
