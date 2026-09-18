package cn.iocoder.txgy.module.crm.framework.ai.core;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * CRM AI 助手 —— 文档文本抽取
 *
 * 支持 AI 助手上传 Word / PDF 合同或报价单，抽取纯文本后交给大模型做字段识别，
 * 复用与图片 OCR 完全一致的识别与建单流程。
 *
 * 说明：
 * 1. .docx 用 POI XWPF 解析（含表格，合同里的产品明细多在表格中）；
 * 2. .pdf 用 PDFBox 解析；
 * 3. 旧版 .doc（二进制格式）与扫描版 PDF（无文字层）无法抽取，会给出明确提示，
 *    不返回空内容冒充识别结果。
 */
@Slf4j
@Component
public class CrmAiDocumentExtractor {

    /**
     * 抽取文本长度上限。
     * 合同动辄几十页，只取前若干字符即可覆盖甲乙方、产品明细等关键信息，
     * 同时避免把大模型上下文挤爆。
     */
    private static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 抽取文档纯文本
     *
     * @param fileName 文件名（用于判断类型，给出更友好的报错）
     * @param bytes    文件内容
     * @return 抽取出的文本
     */
    public String extractText(String fileName, byte[] bytes) {
        String ext = getExtension(fileName);
        String text;
        switch (ext) {
            case "docx" -> text = extractDocx(bytes);
            case "pdf" -> text = extractPdf(bytes);
            case "txt", "md", "csv" -> text = new String(bytes, StandardCharsets.UTF_8);
            case "doc" -> throw new IllegalArgumentException(
                    "暂不支持旧版 .doc 格式，请用 Word 另存为 .docx，或导出为 PDF 后重新上传");
            default -> throw new IllegalArgumentException(
                    "暂不支持该文件格式（." + ext + "），请上传 .docx、.pdf 或图片");
        }
        text = StrUtil.trimToEmpty(text);
        if (StrUtil.isBlank(text)) {
            throw new IllegalArgumentException(
                    "未能从文档中读取到文字内容（可能是扫描件或图片型 PDF），请改用图片上传识别");
        }
        return StrUtil.sub(text, 0, MAX_TEXT_LENGTH);
    }

    private String extractDocx(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    sb.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    // 产品明细通常在表格里，按“单元格 + 制表符”拼成一行，便于模型识别
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            sb.append(cell.getText()).append('\t');
                        }
                        sb.append('\n');
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[extractDocx][解析 Word 失败]", e);
            throw new IllegalArgumentException("Word 文档解析失败，请确认文件未损坏后重试");
        }
        return sb.toString();
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("[extractPdf][解析 PDF 失败]", e);
            throw new IllegalArgumentException("PDF 解析失败，请确认文件未加密或损坏后重试");
        }
    }

    private String getExtension(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }

}
