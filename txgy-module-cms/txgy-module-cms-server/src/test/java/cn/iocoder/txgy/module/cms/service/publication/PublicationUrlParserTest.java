package cn.iocoder.txgy.module.cms.service.publication;

import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import cn.iocoder.txgy.module.cms.service.review.ReviewParser;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_NOT_DOCUMENT;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.Validity.EFFECTIVE;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.Validity.UNKNOWN;

class PublicationUrlParserTest {

    @Test
    void nationalLawDatabaseHomePageIsNotADocument() {
        assertThat(PublicationUrlParser.isLandingPage(URI.create("https://flk.npc.gov.cn/"))).isTrue();
        assertThat(PublicationUrlParser.isLandingPage(URI.create("https://flk.npc.gov.cn/search"))).isTrue();
        assertThat(PublicationUrlParser.isLandingPage(URI.create("https://flk.npc.gov.cn/detail2.html?id=test"))).isFalse();
        assertThat(PublicationUrlParser.isLandingPage(
                URI.create("https://wb.flk.npc.gov.cn/flfg/texthtml/test.html"))).isFalse();
    }

    @Test
    void recognizesCurrentNationalLawDatabaseDetailUrl() {
        URI uri = URI.create("https://flk.npc.gov.cn/detail?id=ff8080816f135f46016f20f16ee11737"
                + "&fileId=&type=&title=%E4%B8%AD%E5%8D%8E%E4%BA%BA%E6%B0%91%E5%85%B1%E5%92%8C%E5%9B%BD"
                + "%E5%8A%B3%E5%8A%A8%E6%B3%95");

        assertThat(PublicationUrlParser.isNationalLawDetail(uri)).isTrue();
        assertThat(PublicationUrlParser.nationalLawId(uri))
                .isEqualTo("ff8080816f135f46016f20f16ee11737");
    }

    @Test
    void onlyMapsKnownNationalLawValidityCodeAutomatically() {
        assertThat(PublicationUrlParser.nationalLawValidity(3, UNKNOWN)).isEqualTo(EFFECTIVE);
        assertThat(PublicationUrlParser.nationalLawValidity(2, UNKNOWN)).isEqualTo(UNKNOWN);
    }

    @Test
    void officialHttpsDocumentLinksRemainAllowed() {
        assertThat(ReviewEngine.officialUrl("https://flk.npc.gov.cn/detail2.html?id=test")).isTrue();
        assertThat(ReviewEngine.officialUrl("https://wb.flk.npc.gov.cn/flfg/texthtml/test.html")).isTrue();
        assertThat(ReviewEngine.officialUrl("http://flk.npc.gov.cn/detail2.html?id=test")).isFalse();
    }

    @Test
    void parsingLandingPageReturnsSpecificBusinessError() {
        PublicationUrlParser parser = new PublicationUrlParser(null, new ReviewParser());

        assertThatThrownBy(() -> parser.parse("https://flk.npc.gov.cn/"))
                .hasFieldOrPropertyWithValue("code", PUBLICATION_URL_NOT_DOCUMENT.getCode())
                .hasMessage(PUBLICATION_URL_NOT_DOCUMENT.getMsg());
    }
}
