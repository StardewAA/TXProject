package cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalExtractionStatusEnum;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 抽取编排测试（Mockito 抽取器替身，无 Spring 上下文）：
 * 空结果→PDF_EXTRACT_EMPTY(106_000) 直接失败；成功→SUCCESS 落 extraction_*；OpenDataLoader 未配置→明确失败。
 */
class PdfExtractServiceTest {

    private static File anyPdf() {
        return new File("target/unittest-not-exist.pdf");
    }

    @Test
    void extractOrThrow_emptyResult_throwsPdfExtractEmpty() {
        PdfExtractService service = new PdfExtractService(pdf -> new PdfExtractResult(null, null, null));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.extractOrThrow(anyPdf()));
        assertEquals(ErrorCodeConstants.PDF_EXTRACT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void extractOrThrow_blankTextAndEmptyTables_throwsPdfExtractEmpty() {
        // 扫描件典型形态：抽取器返回空白文本 + 空表格
        PdfExtractService service = new PdfExtractService(
                pdf -> new PdfExtractResult("   \n  ", List.of(), 3));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.extractOrThrow(anyPdf()));
        assertEquals(1_060_106_000, ex.getCode());
    }

    @Test
    void extractOrThrow_textOnly_returnsResult() {
        PdfExtractService service = new PdfExtractService(
                pdf -> new PdfExtractResult("第一章 招标公告\n", null, 1));

        PdfExtractResult result = service.extractOrThrow(anyPdf());
        assertTrue(result.getText().contains("招标公告"));
    }

    @Test
    void extractOrThrow_tablesOnly_returnsResult() {
        // 表格形态：无正文文本但抽到结构化表格 → 不算空（表格本身是有效内容）
        PdfExtractService service = new PdfExtractService(
                pdf -> new PdfExtractResult(null, List.of(List.of("标段", "限价")), 1));

        assertNotNull(service.extractOrThrow(anyPdf()));
    }

    @Test
    void applyToNotice_writesSuccessFields() {
        PdfExtractService service = new PdfExtractService(
                pdf -> new PdfExtractResult("某项目招标\n", List.of(List.of("a", "b")), 1));
        NoticeExternalDO notice = new NoticeExternalDO();

        service.applyToNotice(notice, service.extractOrThrow(anyPdf()));

        assertEquals(NoticeExternalExtractionStatusEnum.SUCCESS.getStatus(), notice.getExtractionStatus());
        assertEquals("某项目招标\n", notice.getExtractionText());
        assertEquals("[[\"a\",\"b\"]]", notice.getExtractionTablesJson());
        assertNotNull(notice.getExtractionAt());
    }

    @Test
    void markEngineFailure_setsFailState() {
        PdfExtractService service = new PdfExtractService(pdf -> new PdfExtractResult("x", null, 1));
        NoticeExternalDO notice = new NoticeExternalDO();

        service.markEngineFailure(notice, "bad pdf");

        assertEquals(NoticeExternalExtractionStatusEnum.FAIL.getStatus(), notice.getExtractionStatus());
        assertNotNull(notice.getExtractionAt());
        assertTrue(notice.getExtraRaw().contains("extractionFail=bad pdf"));
    }

    @Test
    void openDataLoaderDefaultUnconfigured_failsLoudly() {
        OpenDataLoaderPdfContentExtractor extractor = new OpenDataLoaderPdfContentExtractor();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> extractor.extract(anyPdf()));
        assertTrue(ex.getMessage().contains("OpenDataLoader"));
    }

}
