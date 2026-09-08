package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 秦源栏目 JSON 解析：res.rows → entries（title/url 绝对化/日期/栏目）。

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QyzJsonListParserTest {

    private static String sampleJson() {
        return """
                {"code":0,"res":{"total":1483,"pageNo":2,"hasNext":true,"rows":[
                {"title":"某项目变更公告","url":"/3ywgg/20260827/1277640981956526080.html",
                "categoryName":"变更公告","publishDate":"2026-08-27T14:49:07.000+0800","text":"<p>正文全文</p>"}]}}
                """;
    }

    @Test
    void parsesRowsToEntries() {
        ParseInput input = new ParseInput();
        input.setPayloadType("JSON");
        input.setContent(sampleJson());
        NoticeParseResult result = new QyzJsonListParser().parse(input);
        assertEquals(NoticeParseResult.Status.SUCCESS, result.getStatus());
        assertEquals("LIST", result.getPageType());
        assertEquals(1, result.getEntries().size());
        NoticeParseResult.ListEntry entry = result.getEntries().get(0);
        assertEquals("某项目变更公告", entry.getTitle());
        assertEquals("https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260827/1277640981956526080.html", entry.getUrl());
        assertEquals("2026-08-27", entry.getDate());
        assertEquals("变更公告", entry.getCategory());
        assertEquals("2", result.getMeta().get("pageNo"));
    }

    @Test
    void nonListJson_unknown() {
        ParseInput input = new ParseInput();
        input.setPayloadType("JSON");
        input.setContent("{\"a\":1}");
        NoticeParseResult result = new QyzJsonListParser().parse(input);
        assertEquals("UNKNOWN", result.getPageType());
    }

}
