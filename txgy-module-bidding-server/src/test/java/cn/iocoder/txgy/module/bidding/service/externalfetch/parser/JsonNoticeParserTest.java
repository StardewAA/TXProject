package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] JSON_GENERIC 解析器：包装字段定位、键名归一映射、数组取首、时间解析。

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNoticeParserTest {

    @Test
    void wrappedPayload_fieldMapping_success() {
        String json = "{\"code\":0,\"data\":{\"title\":\"某项目招标公告\",\"projectCode\":\"P-2026-001\","
                + "\"issuerName\":\"陕西某公司\",\"publishTime\":\"2026-09-01 10:00:00\",\"content\":\"正文...\"}}";
        NoticeParseResult result = new JsonNoticeParser().parse(input(json));
        assertEquals(NoticeParseResult.Status.SUCCESS, result.getStatus());
        assertEquals("某项目招标公告", result.getTitle());
        assertEquals("P-2026-001", result.getProjectCode());
        assertEquals("陕西某公司", result.getIssuerName());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0, 0), result.getPublishTime());
        assertEquals("data", result.getMeta().get("payloadLocation"));
    }

    @Test
    void rootArray_takesFirst_withWarning() {
        String json = "[{\"title\":\"第一条公告\",\"content\":\"正文a\"},{\"title\":\"第二条\"}]";
        NoticeParseResult result = new JsonNoticeParser().parse(input(json));
        assertEquals("第一条公告", result.getTitle());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("数组")));
    }

    @Test
    void epochMillisTime_parsed() {
        LocalDate past = LocalDate.now().minusDays(2);
        long millis = past.atStartOfDay().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        String json = "{\"title\":\"t\",\"content\":\"c\",\"publishTime\":" + millis + "}";
        NoticeParseResult result = new JsonNoticeParser().parse(input(json));
        assertEquals(past.atStartOfDay(), result.getPublishTime());
    }

    @Test
    void malformedJson_failed() {
        NoticeParseResult result = new JsonNoticeParser().parse(input("{broken"));
        assertEquals(NoticeParseResult.Status.FAILED, result.getStatus());
        assertNotNull(result.getError());
    }

    @Test
    void chineseAliasKeys_mapped() {
        String json = "{\"公告标题\":\"xxx\",\"项目编号\":\"XM-1\",\"发布单位\":\"某单位\",\"正文\":\"正文\"}";
        NoticeParseResult result = new JsonNoticeParser().parse(input(json));
        assertEquals("xxx", result.getTitle());
        assertEquals("XM-1", result.getProjectCode());
        assertEquals("某单位", result.getIssuerName());
    }

    private static ParseInput input(String json) {
        ParseInput input = new ParseInput();
        input.setPayloadType("JSON");
        input.setContent(json);
        return input;
    }

}
