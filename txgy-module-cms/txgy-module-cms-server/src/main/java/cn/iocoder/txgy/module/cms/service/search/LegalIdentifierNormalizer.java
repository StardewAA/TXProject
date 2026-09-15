package cn.iocoder.txgy.module.cms.service.search;

import java.text.Normalizer;
import java.util.Locale;

/** 法律文号、标准编号和条款编号的精确检索规范化工具。 */
public final class LegalIdentifierNormalizer {

    private LegalIdentifierNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s·•—–－_\\-]+", "")
                .trim();
    }

}
