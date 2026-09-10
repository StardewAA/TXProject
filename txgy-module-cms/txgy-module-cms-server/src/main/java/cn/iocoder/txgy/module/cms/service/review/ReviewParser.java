package cn.iocoder.txgy.module.cms.service.review;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

@Component
public class ReviewParser {
    public static final int MAX_BYTES = 25 * 1024 * 1024;
    public static final int MAX_CHARS = 1_000_000;
    private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public Parsed parse(MultipartFile file) {
        List<Issue> issues = new ArrayList<>();
        if (file == null || file.isEmpty()) return failure("EMPTY_FILE", "文档为空，请上传正文文件");
        if (file.getSize() > MAX_BYTES) return failure("FILE_LIMIT", "文件超过25MB限制");
        String filename = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) return failure("FILE_LIMIT", "文件超过25MB限制");
            String text;
            if (filename.endsWith(".txt")) {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if (text.startsWith("\uFEFF")) text = text.substring(1);
            } else if (filename.endsWith(".docx")) {
                text = docx(bytes, issues);
            } else if (filename.endsWith(".pdf")) {
                text = pdf(bytes, issues);
            } else return failure("UNSUPPORTED_TYPE", "仅支持UTF-8 TXT、DOCX和PDF，请转换文件格式");
            if (text.length() > MAX_CHARS) return failure("TEXT_LIMIT", "正文超过100万字符，请分卷提交");
            return new Parsed(text, List.copyOf(issues));
        } catch (CharacterCodingException e) {
            return failure("INVALID_UTF8", "TXT不是有效UTF-8文本，请重新编码后提交");
        } catch (Exception e) {
            return failure("PARSE_FAILED", "文档损坏、加密、格式不匹配或解析超过限制，请提供可读原文");
        }
    }

    private String pdf(byte[] bytes, List<Issue> issues) throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent()) throw new IOException("Protected PDF");
            if (pdf.getNumberOfPages() > 300) throw new IOException("Too many pages");
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            for (int page=1; page<=pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                String content = stripper.getText(pdf);
                if (content.strip().length() < 20) issues.add(new Issue(Dimension.QUALITY, Severity.ERROR,
                        "OCR_REQUIRED", "PDF第" + page + "页", "页面缺少可读取正文，可能是扫描页或图片页", "完成OCR并核对图片、表格和数值后重交"));
                text.append(content).append('\n');
                if (text.length() > MAX_CHARS) throw new IOException("Too much text");
            }
            issues.add(new Issue(Dimension.FORMAT, Severity.WARNING, "PDF_LAYOUT", "全文",
                    "PDF文字提取不保证表格、公式与图像顺序完整", "核验人员需对照原PDF检查版面和数值"));
            return text.toString();
        }
    }

    private String docx(byte[] bytes, List<Issue> issues) throws Exception {
        byte[] document = null;
        long expanded = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 2000) throw new IOException("Too many ZIP entries");
                byte[] part = zip.readNBytes(MAX_BYTES + 1);
                expanded += part.length;
                if (part.length > MAX_BYTES || expanded > 30L * MAX_BYTES) throw new IOException("ZIP expansion limit");
                if ("word/document.xml".equals(entry.getName())) {
                    if (document != null) throw new IOException("Duplicate document");
                    document = part;
                }
                if (entry.getName().startsWith("word/embeddings/") && !entry.isDirectory())
                    issues.add(new Issue(Dimension.QUALITY, Severity.ERROR, "EMBEDDED_DOCUMENT", "附件",
                            "文档含嵌入附件，本次正文不包含附件内容", "将内嵌PDF或其他文件拆出单独提交"));
            }
        }
        if (document == null) throw new IOException("Not DOCX");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(document));
        if (doc.getElementsByTagNameNS(WORD, "del").getLength() > 0 || doc.getElementsByTagNameNS(WORD, "ins").getLength() > 0)
            issues.add(new Issue(Dimension.FORMAT, Severity.ERROR, "TRACKED_CHANGES", "全文", "存在未处理的修订痕迹", "接受或拒绝修订后重交正式版"));
        if (doc.getElementsByTagNameNS(WORD, "drawing").getLength() > 0)
            issues.add(new Issue(Dimension.QUALITY, Severity.WARNING, "IMAGES", "全文", "正文含图片，图片内容未自动识别", "核验人员须核对图片是否包含条款或关键数据"));
        StringBuilder text = new StringBuilder();
        append(doc.getDocumentElement(), text);
        return text.toString();
    }

    private void append(Node node, StringBuilder out) throws IOException {
        if (WORD.equals(node.getNamespaceURI()) && "del".equals(node.getLocalName())) return;
        if (WORD.equals(node.getNamespaceURI()) && "t".equals(node.getLocalName())) out.append(node.getTextContent());
        else for (Node child=node.getFirstChild(); child!=null; child=child.getNextSibling()) append(child,out);
        if (WORD.equals(node.getNamespaceURI())) {
            if (Set.of("p","br","tr").contains(node.getLocalName())) out.append('\n');
            if (Set.of("tab","tc").contains(node.getLocalName())) out.append('\t');
        }
        if (out.length()>MAX_CHARS) throw new IOException("Too much text");
    }

    private Parsed failure(String code, String message) {
        return new Parsed("", List.of(new Issue(Dimension.ENCODING, Severity.ERROR, code, "文件", message, "修正文档后重新提交")));
    }
}
