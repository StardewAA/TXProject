package cn.iocoder.txgy.module.cms.service.publication;

import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_NOT_DOCUMENT;

class PublicationUrlParserTest {

    @Test
    void nationalLawDatabaseHomePageIsNotADocument() {
        assertThat(PublicationUrlParser.isLandingPage(URI.create("https://flk.npc.gov.cn/"))).isTrue();
        assertThat(PublicationUrlParser.isLandingPage(URI.create("https://flk.npc.gov.cn/detail2.html?id=test"))).isFalse();
        assertThat(PublicationUrlParser.isLandingPage(
                URI.create("https://wb.flk.npc.gov.cn/flfg/texthtml/test.html"))).isFalse();
    }

    @Test
    void officialHttpsDocumentLinksRemainAllowed() {
        assertThat(ReviewEngine.officialUrl("https://flk.npc.gov.cn/detail2.html?id=test")).isTrue();
        assertThat(ReviewEngine.officialUrl("https://wb.flk.npc.gov.cn/flfg/texthtml/test.html")).isTrue();
        assertThat(ReviewEngine.officialUrl("http://flk.npc.gov.cn/detail2.html?id=test")).isFalse();
    }

    @Test
    void parsingLandingPageReturnsSpecificBusinessError() {
        PublicationUrlParser parser = new PublicationUrlParser(null);

        assertThatThrownBy(() -> parser.parse("https://flk.npc.gov.cn/"))
                .hasFieldOrPropertyWithValue("code", PUBLICATION_URL_NOT_DOCUMENT.getCode())
                .hasMessage(PUBLICATION_URL_NOT_DOCUMENT.getMsg());
    }
}
