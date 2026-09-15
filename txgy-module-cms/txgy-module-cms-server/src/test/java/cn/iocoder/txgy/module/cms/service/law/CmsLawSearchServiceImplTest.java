package cn.iocoder.txgy.module.cms.service.law;

import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CmsLawSearchServiceImplTest {

    @Test
    void shouldBuildTenantSafeExactAndFullTextQuery() {
        CmsLawSearchReqDTO request = new CmsLawSearchReqDTO();
        request.setKeyword("ＧＢ ５０２６４－２０１３");
        request.setIndustryCode("PETROCHEMICAL");
        request.setRegionCode("CN");
        request.setLawType("STANDARD");
        request.setStatus(CmsLawStatusEnum.EFFECTIVE);
        request.setAsOfDate(LocalDate.of(2026, 9, 4));

        Query query = CmsLawSearchServiceImpl.buildSearchQuery(request, 2005L);
        String json = query.toString();

        assertThat(json)
                .contains("tenant_id", "2005", "published")
                .contains("document_no_normalized", "article_no_normalized", "GB502642013")
                .contains("title^5", "content^2")
                .contains("industry_code", "PETROCHEMICAL")
                .contains("region_code", "CN")
                .contains("law_type", "STANDARD")
                .contains("effective_date", "expiry_date", "2026-09-04");
    }

    @Test
    void shouldMapElasticsearchSourceToApiResponse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("law_id", 101L);
        source.put("article_id", 202L);
        source.put("source_id", "MOHURD-GB50264-2013");
        source.put("title", "工业设备及管道绝热工程设计规范");
        source.put("document_no", "GB 50264-2013");
        source.put("article_no", "第5.1.2条");
        source.put("content", "条款正文");
        source.put("version", "2013");
        source.put("issuing_authority", "住房和城乡建设部");
        source.put("source_url", "https://example.test/source");
        source.put("effective_date", "2013-10-01");
        source.put("status", "EFFECTIVE");
        Hit<Map> hit = Hit.of(builder -> builder
                .index("txgy-cms-law-v1-000001")
                .id("202")
                .score(18.25)
                .source(source));

        CmsLawSearchRespDTO result = CmsLawSearchServiceImpl.toResponse(hit);

        assertThat(result.getLawId()).isEqualTo(101L);
        assertThat(result.getArticleId()).isEqualTo(202L);
        assertThat(result.getDocumentNo()).isEqualTo("GB 50264-2013");
        assertThat(result.getArticleNo()).isEqualTo("第5.1.2条");
        assertThat(result.getEffectiveDate()).isEqualTo(LocalDate.of(2013, 10, 1));
        assertThat(result.getStatus()).isEqualTo(CmsLawStatusEnum.EFFECTIVE);
        assertThat(result.getScore()).isEqualTo(18.25);
    }

}
