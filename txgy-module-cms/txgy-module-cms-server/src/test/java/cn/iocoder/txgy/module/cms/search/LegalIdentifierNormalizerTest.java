package cn.iocoder.txgy.module.cms.search;

import cn.iocoder.txgy.module.cms.service.search.LegalIdentifierNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalIdentifierNormalizerTest {

    @Test
    void normalizesFullWidthAndSpacingWithoutChangingLegalCharacters() {
        assertThat(LegalIdentifierNormalizer.normalize("ＧＢ ５０２６４－２０１３"))
                .isEqualTo("GB502642013");
        assertThat(LegalIdentifierNormalizer.normalize("第二百三十二条"))
                .isEqualTo("第二百三十二条");
    }
}
