package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageCountRespVO;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档页数识别 Service
 *
 * <p>用于「文档资源库 - 新增文档」自动识别文档总页数，并按
 * <b>预估阅读时长 = 总页数 × 10 分钟/页</b> 换算阅读时长。</p>
 *
 * <p>识别策略：</p>
 * <ul>
 *   <li>PDF：直接读取页数（精确）</li>
 *   <li>JPG/PNG：固定 1 页（精确）</li>
 *   <li>doc/WPS 二进制文档：读取 SummaryInformation 页数（精确），缺失时按内容估算</li>
 *   <li>docx/WPS OOXML 文档：按分页符 + 渲染分页标记统计（精确度较高），缺失时按内容估算</li>
 * </ul>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class ResourcePageCountService {

    /**
     * 每页预估阅读耗时（分钟/页）—— 需求约定：预估阅读时长 = 总页数 × 10 分钟
     */
    public static final int MINUTES_PER_PAGE = 10;

    /**
     * 无分页信息时按字符数估算的每页字数
     */
    private static final int CHARS_PER_PAGE = 1500;

    /**
     * 估算页数上限，避免超大文本算出离谱页数
     */
    private static final int MAX_PAGES = 5000;

    private static final Pattern OOXML_PAGE_BREAK = Pattern.compile("<w:br[^>]*w:type=\"page\"");
    private static final Pattern OOXML_RENDERED_PAGE_BREAK = Pattern.compile("<w:lastRenderedPageBreak");
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");

    /**
     * 识别文档页数并换算预估阅读时长，任何失败场景均兜底为 1 页，不阻断上传流程
     */
    public DocumentPageCountRespVO count(MultipartFile file) {
        String fileName = file == null ? null : file.getOriginalFilename();
        PageCount result = new PageCount(1, false);
        try {
            byte[] bytes = file == null ? null : file.getBytes();
            if (bytes != null && bytes.length > 0) {
                String ext = ResourceConstants.getFileExt(fileName);
                result = switch (ext) {
                    case "pdf" -> new PageCount(countPdfPages(bytes), true);
                    case "jpg", "jpeg", "png" -> new PageCount(1, true);
                    case "doc" -> countBinaryWordPages(bytes);
                    case "docx", "wps" -> countOoxmlWordPages(bytes);
                    default -> new PageCount(estimatePages(bytes.length), false);
                };
            }
        } catch (Exception e) {
            log.warn("[count][文档页数识别失败，按 1 页兜底, fileName={}]", fileName, e);
        }
        int pages = Math.max(1, Math.min(result.pages(), MAX_PAGES));
        DocumentPageCountRespVO resp = new DocumentPageCountRespVO();
        resp.setPageCount(pages);
        resp.setMinutesPerPage(MINUTES_PER_PAGE);
        resp.setEstMinutes(pages * MINUTES_PER_PAGE);
        resp.setPrecise(result.precise());
        return resp;
    }

    /**
     * 按页数换算预估阅读时长（分钟）
     */
    public int calcEstMinutes(Integer pageCount) {
        int pages = pageCount == null ? 1 : Math.max(1, Math.min(pageCount, MAX_PAGES));
        return pages * MINUTES_PER_PAGE;
    }

    // ========== 各类型识别 ==========

    private int countPdfPages(byte[] bytes) throws Exception {
        try (PDDocument document = PDDocument.load(bytes)) {
            return document.getNumberOfPages();
        }
    }

    private PageCount countBinaryWordPages(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(in)) {
            SummaryInformation summary = document.getSummaryInformation();
            if (summary != null && summary.getPageCount() > 0) {
                return new PageCount(summary.getPageCount(), true);
            }
            // 老版 Word 未保存分页信息时，退回按字符数估算
            String text = new WordExtractor(document).getText();
            return new PageCount(estimatePages(StrUtil.length(text)), false);
        } catch (Exception e) {
            log.warn("[countBinaryWordPages][Word/WPS 文档解析失败: {}]", e.getMessage());
            return new PageCount(1, false);
        }
    }

    private PageCount countOoxmlWordPages(byte[] bytes) {
        String xml = readZipEntry(bytes, "word/document.xml");
        if (StrUtil.isBlank(xml)) {
            return new PageCount(1, false);
        }
        int breaks = countMatches(OOXML_PAGE_BREAK, xml) + countMatches(OOXML_RENDERED_PAGE_BREAK, xml);
        if (breaks > 0) {
            return new PageCount(breaks + 1, true);
        }
        // 未显式分页（多为自动分页），按正文字符数估算
        String text = XML_TAG.matcher(xml).replaceAll("");
        return new PageCount(estimatePages(text.length()), false);
    }

    // ========== 工具方法 ==========

    private String readZipEntry(byte[] bytes, String entryName) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zin.transferTo(out);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("[readZipEntry][读取 {} 失败: {}]", entryName, e.getMessage());
        }
        return null;
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int estimatePages(int charCount) {
        if (charCount <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(charCount / (double) CHARS_PER_PAGE));
    }

    /**
     * 页数识别结果
     *
     * @param pages   页数
     * @param precise 是否精确识别
     */
    private record PageCount(int pages, boolean precise) {
    }

}
