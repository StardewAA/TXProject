package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// 真实样本校准（样本位于仓库 docs/爬虫网站和测试数据/；文件缺失时自动跳过，不阻断常规构建）。
// 依据样本：秦源(qyzb.shccmg.com) 详情 HTML×3 + 国信 e 采 PDF×1。

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalibrationSamplesTest {

    private static final String SAMPLES = "D:/Code/IDEA/txgy/txgycloud/txgy-module-bidding/docs/爬虫网站和测试数据/";

    @Test
    void qyzHtmlSamples() throws Exception {
        String[] files = {
                "秦源招标电子招标平台/view-source_https___qyzb.shccmg.com_cms_default_webfile_zbgg_20260907_1281536897327300608.html",
                "秦源招标电子招标平台/1280525599890210816.html",
                "秦源招标电子招标平台/view-source_https___qyzb.shccmg.com_cms_default_webfile_zbgs_20260905_1280965959208665088.html",
        };
        for (String rel : files) {
            Path file = Path.of(SAMPLES + rel);
            assumeTrue(Files.exists(file), "样本缺失，跳过: " + rel);
            String html = Files.readString(file);
            ParseInput input = new ParseInput();
            input.setPayloadType("HTML");
            input.setContent(html);
            input.setUrl("https://qyzb.shccmg.com/cms/default/webfile/zbgg/20260907/x.html");
            NoticeParseResult result = new QyzNoticeParser().parse(input);
            System.out.println("== QYZB " + file.getFileName() + " ==");
            System.out.println("status=" + result.getStatus() + " title=" + result.getTitle()
                    + " publish=" + result.getPublishTime() + " type=" + result.getNoticeTypeRaw());
            System.out.println("tenderCode=" + result.getTenderProjectCode() + " issuer=" + result.getIssuerName());
            assertTrue(result.getStatus() != NoticeParseResult.Status.FAILED, rel + " 应可解析");
            assertNotNull(result.getTitle(), rel + " 标题缺失");
            assertNotNull(result.getPublishTime(), rel + " 发布时间缺失");
            assertTrue(result.getTenderProjectCode() != null && result.getTenderProjectCode().length() >= 8,
                    rel + " 招标编号缺失");
        }
    }

    @Test
    void guoxinPdfSamples() throws Exception {
        Path file = Path.of(SAMPLES + "国信e采（山西）/1.pdf");
        assumeTrue(Files.exists(file), "样本缺失，跳过: 1.pdf");
        ParseInput input = new ParseInput();
        input.setPayloadType("PDF");
        input.setFileName("1.pdf");
        input.setFileBytes(Files.readAllBytes(file));
        NoticeParseResult result = new PdfTextNoticeParser().parse(input);
        System.out.println("== GX 1.pdf ==");
        System.out.println("status=" + result.getStatus() + " error=" + result.getError());
        System.out.println("title=" + result.getTitle());
        System.out.println("code=" + result.getProjectCode() + " publish=" + result.getPublishTime()
                + " issuer=" + result.getIssuerName());
        String text = result.getContentText();
        System.out.println("textHead=" + (text == null ? "" : text.replaceAll("\\s+", " ").substring(0, Math.min(600, text.length()))));
        System.out.println("meta=" + result.getMeta());
    }


    @Test
    void qyzHomePageListShell() throws Exception {
        Path file = Path.of(SAMPLES + "view-source_https___qyzb.shccmg.com_cms_default_webfile_1ywgg_index.html");
        assumeTrue(Files.exists(file), "样本缺失，跳过: 1ywgg index");
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(Files.readString(file));
        input.setUrl("https://qyzb.shccmg.com/cms/default/webfile/1ywgg/index.html");
        NoticeParseResult result = new QyzNoticeParser().parse(input);
        System.out.println("== QYZB 1ywgg index（主页壳）== pageType=" + result.getPageType()
                + " entries=" + (result.getEntries() == null ? 0 : result.getEntries().size()));
    }

    @Test
    void gxListIframeSamples() throws Exception {
        Path file = Path.of(SAMPLES + "view-source_https___gx.e-bidding.org_sxyczscms__category_iframe.html_dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1.html");
        assumeTrue(Files.exists(file), "样本缺失，跳过: gx iframe list");
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(Files.readString(file));
        input.setUrl("https://gx.e-bidding.org/sxyczscms/category/iframe.html?dates=300&categoryId=2&tenderMethod=01&tabName=招标公告&page=1");
        NoticeParseResult result = new GxEbidNoticeParser().parse(input);
        System.out.println("== GX iframe list == status=" + result.getStatus() + " pageType=" + result.getPageType()
                + " entries=" + (result.getEntries() == null ? 0 : result.getEntries().size()));
        if (result.getEntries() != null && !result.getEntries().isEmpty()) {
            System.out.println("first=" + result.getEntries().get(0).getTitle().substring(0, Math.min(50, result.getEntries().get(0).getTitle().length()))
                    + " | " + result.getEntries().get(0).getUrl() + " | " + result.getEntries().get(0).getDate());
        }
        if (result.getEntries() != null && !result.getEntries().isEmpty()) {
            org.junit.jupiter.api.Assertions.assertTrue(result.getEntries().get(0).getUrl().contains("gx.e-bidding.org"));
        }
    }


    @Test
    void gxDetailPdfSample() throws Exception {
        Path file = Path.of(SAMPLES + "view-source_https___gx.e-bidding.org_sdny_bulletin_2026-06-18_10835.html");
        assumeTrue(Files.exists(file), "样本缺失，跳过: gx detail 10835");
        ParseInput input = new ParseInput();
        input.setPayloadType("HTML");
        input.setContent(Files.readString(file));
        input.setUrl("https://gx.e-bidding.org/sdny_bulletin/2026-06-18/10835.html");
        NoticeParseResult result = new GxEbidNoticeParser().parse(input);
        System.out.println("== GX detail 10835 == pageType=" + result.getPageType()
                + " pdfUrl=" + result.getMeta().get("pdfDownloadUrl"));
        assertTrue("DETAIL_PDF".equals(result.getPageType()), "应识别为详情 PDF");
        assertTrue(result.getMeta().get("pdfDownloadUrl") != null
                        && result.getMeta().get("pdfDownloadUrl").contains("openFileById?fileType=2&id="),
                "PDF 直链解码失败: " + result.getMeta().get("pdfDownloadUrl"));
    }

}
