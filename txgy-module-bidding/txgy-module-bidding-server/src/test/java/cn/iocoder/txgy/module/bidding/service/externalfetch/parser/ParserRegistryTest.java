package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 解析器注册表：构造收集/重复 code 启动失败/未知 code 明确报错（与 ChannelRegistry 同构）。

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserRegistryTest {

    private static NoticeParser parser(String code) {
        return new NoticeParser() {
            @Override
            public String getParserCode() {
                return code;
            }

            @Override
            public String getParserName() {
                return code;
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public List<String> supportedPayloadTypes() {
                return List.of("HTML");
            }

            @Override
            public NoticeParseResult parse(ParseInput input) {
                return NoticeParseResult.failed(code, code, "unused");
            }
        };
    }

    @Test
    void duplicateCode_failsStartup() {
        ParserRegistry registry = new ParserRegistry(List.of(parser("A"), parser("A")));
        assertThrows(IllegalStateException.class, registry::init);
    }

    @Test
    void unknownCode_throwsWithMessage() {
        ParserRegistry registry = new ParserRegistry(List.of(parser("A")));
        registry.init();
        assertThrows(IllegalArgumentException.class, () -> registry.get("NOPE"));
    }

    @Test
    void hasAndAll() {
        ParserRegistry registry = new ParserRegistry(List.of(parser("B"), parser("A")));
        registry.init();
        assertTrue(registry.has("A"));
        assertFalse(registry.has("NOPE"));
        assertTrue(registry.all().size() == 2);
    }

}
