package cn.iocoder.txgy.module.erp.service.recognition.provider;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDocumentRecognitionProviderTest {

    @Test
    void shouldIgnoreCharsetWhenDetectingFileMediaType() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("image/png;charset=UTF-8"));

        String mediaType = ReflectionTestUtils.invokeMethod(
                HttpDocumentRecognitionProvider.class,
                "detectMediaType",
                java.net.URI.create("http://infra:48080/admin-api/infra/file/4/get/test.png"),
                headers);

        assertEquals("image/png", mediaType);
    }

    @Test
    void shouldNormalizeZhipuHtmlTableBeforeParsing() {
        String response = "{\"result\":\"<table border=\\\"1\\\"><tr><td>供应商名称</td><td>订单日期</td><td>外部单号</td>"
                + "<td>产品编码</td><td>产品名称</td><td>数量</td><td>单价</td><td>税率(%)</td><td>备注</td></tr>"
                + "<tr><td>测试供应商A</td><td>2026-09-09</td><td>PO-TEST-20260909-001</td>"
                + "<td>TEST-PIPE-001</td><td>测试防腐保温管</td><td>10.00</td><td>128.50</td><td>13.00</td>"
                + "<td>Excel导入测试-第一条明细</td></tr></table>\"}";

        String text = ReflectionTestUtils.invokeMethod(HttpDocumentRecognitionProvider.class, "extractText", response);
        TesseractDocumentRecognitionProvider parser = new TesseractDocumentRecognitionProvider();
        String normalized = parser.buildRecognitionResponse(text, "PURCHASE_ORDER", "ZHIPU_GLM_OCR");
        ErpRecognitionDocument document = JsonUtils.parseObject(normalized, ErpRecognitionDocument.class);

        assertEquals("测试供应商A", document.getSupplierName());
        assertEquals("2026-09-09", document.getDocumentDate());
        assertEquals("PO-TEST-20260909-001", document.getDocumentNo());
        assertEquals(1, document.getItems().size());
        assertEquals("TEST-PIPE-001", document.getItems().getFirst().getProductCode());
        assertEquals("测试防腐保温管", document.getItems().getFirst().getProductName());
        assertTrue(document.getItems().getFirst().getCount().compareTo(new java.math.BigDecimal("10.00")) == 0);
        assertTrue(document.getItems().getFirst().getProductPrice().compareTo(new java.math.BigDecimal("128.50")) == 0);
        assertTrue(document.getItems().getFirst().getTaxPercent().compareTo(new java.math.BigDecimal("13.00")) == 0);
        assertEquals("Excel导入测试-第一条明细", document.getItems().getFirst().getRemark());
    }

    @Test
    void shouldPreferZhipuMarkdownResultForPurchaseDetails() {
        String response = "{\"layout_details\":[[{\"content\":\"供应商名称\"},{\"content\":\"测试供应商B\"},"
                + "{\"content\":\"产品编码\"},{\"content\":\"PIPE-002\"}]],"
                + "\"md_results\":\"|供应商名称|订单日期|外部单号|产品编码|产品名称|数量|单价|税率(%)|备注|\\n"
                + "|---|---|---|---|---|---|---|---|---|\\n"
                + "|测试供应商B|2026-09-10|PO-TEST-20260910-002|PIPE-002|测试保温管|5|88.80|9%|第二条明细备注|\"}";

        String text = ReflectionTestUtils.invokeMethod(HttpDocumentRecognitionProvider.class, "extractText", response);
        TesseractDocumentRecognitionProvider parser = new TesseractDocumentRecognitionProvider();
        ErpRecognitionDocument document = JsonUtils.parseObject(
                parser.buildRecognitionResponse(text, "PURCHASE_ORDER", "ZHIPU_GLM_OCR"),
                ErpRecognitionDocument.class);

        assertEquals("测试供应商B", document.getSupplierName());
        assertEquals("2026-09-10", document.getDocumentDate());
        assertEquals("PO-TEST-20260910-002", document.getDocumentNo());
        assertEquals(1, document.getItems().size());
        assertEquals("PIPE-002", document.getItems().getFirst().getProductCode());
        assertEquals("测试保温管", document.getItems().getFirst().getProductName());
        assertTrue(document.getItems().getFirst().getTaxPercent().compareTo(new java.math.BigDecimal("9")) == 0);
        assertEquals("第二条明细备注", document.getItems().getFirst().getRemark());
    }
}
