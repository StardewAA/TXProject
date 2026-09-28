package cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf;
// 规范依据：设计文档 v0.6 §7.3/§15.6（本地 OpenDataLoader 抽取；空结果=FAIL_EMPTY 报错失败，错误码 1-060-106-000）；§7.4（extraction_* 落库）

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalExtractionStatusEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.PDF_EXTRACT_EMPTY;

/**
 * PDF 抽取编排：调抽取器 → 空结果直接抛 {@link PDF_EXTRACT_EMPTY}（FAIL_EMPTY 判失败，不入库展示）；
 * 成功结果回写 NoticeExternalDO 的 extraction_* 列（SUCCESS）。扫描件等纯图 PDF 由引擎返回空文本表格，统一在此裁决失败。
 */
@Slf4j
public class PdfExtractService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PdfContentExtractor extractor;

    public PdfExtractService() {
        this(new OpenDataLoaderPdfContentExtractor());
    }

    /** 测试/替换通道用：注入自定义抽取器 */
    public PdfExtractService(PdfContentExtractor extractor) {
        this.extractor = extractor;
    }

    public PdfContentExtractor getExtractor() {
        return extractor;
    }

    /**
     * 抽取并校验：空结果（无文本且无表格）抛 PDF_EXTRACT_EMPTY（错误码 106_000，扫描件直接判失败）。
     *
     * @return 非空抽取结果
     */
    public PdfExtractResult extractOrThrow(File pdf) {
        PdfExtractResult result = extractor.extract(pdf);
        if (result == null || result.isEmptyResult()) {
            // 扫描件/空结果：按设计 §15.6 报错失败（不转审核、不入库展示）
            log.warn("[extractOrThrow][pdf={}] PDF 抽取结果为空（扫描件或无可提取文本/表格）", pdf.getAbsolutePath());
            throw exception(PDF_EXTRACT_EMPTY);
        }
        return result;
    }

    /**
     * 将成功抽取结果回写主档（extraction_status=SUCCESS + 文本/表格/时间）。
     */
    public void applyToNotice(NoticeExternalDO notice, PdfExtractResult result) {
        notice.setExtractionStatus(NoticeExternalExtractionStatusEnum.SUCCESS.getStatus());
        notice.setExtractionText(result.getText());
        notice.setExtractionTablesJson(serializeTables(result));
        notice.setExtractionAt(LocalDateTime.now());
    }

    /** 引擎异常路径：把状态落 FAIL（区别于空结果 FAIL_EMPTY），便于人工排查后重试 */
    public void markEngineFailure(NoticeExternalDO notice, String message) {
        notice.setExtractionStatus(NoticeExternalExtractionStatusEnum.FAIL.getStatus());
        notice.setExtractionAt(LocalDateTime.now());
        notice.setExtraRaw(appendFailureMessage(notice.getExtraRaw(), message));
    }

    private String serializeTables(PdfExtractResult result) {
        if (result.getTables() == null || result.getTables().isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(result.getTables());
        } catch (JsonProcessingException e) {
            // 表格序列化失败不应中断主流程：置空表格但保留文本（文本非空即视为抽取成功）
            log.warn("[applyToNotice][tables] 表格 JSON 序列化失败，按空表格处理: {}", e.getMessage());
            return "[]";
        }
    }

    private String appendFailureMessage(String extraRaw, String message) {
        String fail = "extractionFail=" + message;
        if (extraRaw == null || extraRaw.isBlank()) {
            return fail;
        }
        return extraRaw + ";" + fail;
    }

}
