package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] QYZB 站点规则单元测试（合成秦源详情页结构：返回列表标题/发布时间/招标编号/招标人/推荐区）

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QyzNoticeParserTest {

    private String qyzHtml(String title, String code, String issuer) {
        return "<html><head><title>秦源招标电子招标平台</title></head><body>"
                + "<div>首页 业务公告</div>"
                + "<div>详情 返回列表 " + title + "</div>"
                + "<h1>" + title + "</h1>"
                + "<span>发布时间：2026-09-07 09:12:33</span>"
                + "<div>某单位</div>"
                + "<div>" + title + "（招标编号：" + code + "）一、项目概况 正文内容段落。</div>"
                + "<div>供应商征集公告 xx公告 秦源资讯</div>"
                + "<div>四、联系方式 招标人：" + issuer + " 地址：xx</div>"
                + "</body></html>";
    }

    @Test
    void parsesQyzDetailFields() {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(qyzHtml("某选煤厂技改工程招标公告", "0866-26A1SXQY0001", "陕西某某矿业有限公司"));
        input.setUrl("https://qyzb.shccmg.com/cms/default/webfile/zbgg/20260907/1.html");

        NoticeParseResult result = new QyzNoticeParser().parse(input);

        assertEquals(NoticeParseResult.Status.SUCCESS, result.getStatus());
        assertEquals("某选煤厂技改工程招标公告", result.getTitle());
        assertEquals("0866-26A1SXQY0001", result.getTenderProjectCode());
        assertEquals("陕西某某矿业有限公司", result.getIssuerName());
        assertNotNull(result.getPublishTime());
        assertEquals(2026, result.getPublishTime().getYear());
        assertEquals("招标公告", result.getNoticeTypeRaw());
        // 正文应避开导航/推荐区：含正文关键词
        assertTrue(result.getContentText().contains("一、项目概况"));
        assertTrue(!result.getContentText().contains("首页"));
    }

    @Test
    void zhongbiaoHouXuanRen_publishedTypeInferred() {
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(qyzHtml("某装置检维修项目中标候选人公示", "0866-26B3SXQY0451", "陕西某化工有限公司"));
        NoticeParseResult result = new QyzNoticeParser().parse(input);
        assertEquals("中标候选人公示", result.getNoticeTypeRaw());
    }

}
