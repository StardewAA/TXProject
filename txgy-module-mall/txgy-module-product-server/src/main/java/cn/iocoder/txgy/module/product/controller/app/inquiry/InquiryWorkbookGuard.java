package cn.iocoder.txgy.module.product.controller.app.inquiry;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

/** 仅用于小型询价模板：流式计量实际解压字节，不信任ZIP声明的大小。 */
final class InquiryWorkbookGuard {
    private static final long MAX_ENTRY = 8L * 1024 * 1024;
    private static final long MAX_TOTAL = 16L * 1024 * 1024;
    private InquiryWorkbookGuard() {}

    static void validate(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4)
            throw invalid("请使用未加密的标准.xlsx模板文件");
        var names = new HashSet<String>();
        long total = 0;
        int count = 0;
        byte[] buffer = new byte[8192];
        try {
          // 中央目录读取兼容平台模板的ZIP64数据描述；临时文件仅为有上限的上传原始字节，不解压落盘。
          var temporary = Files.createTempFile("mall-inquiry-check-", ".xlsx");
          try {
           Files.write(temporary, bytes);
           try (var zip = new ZipFile(temporary.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (++count > 256) throw invalid("Excel内部文件过多，请复制采购数据到平台空白模板后重试");
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".bin") || lower.startsWith("xl/externallinks/")
                        || lower.startsWith("xl/activex/") || lower.startsWith("xl/embeddings/")
                        || lower.startsWith("xl/macrosheets/"))
                    throw invalid("询价模板不能包含宏、外部数据链接或嵌入对象，请复制为纯文本或数值后导入");
                if (name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || java.util.Arrays.asList(name.split("/")).contains("..") || !names.add(name))
                    throw invalid("Excel内部文件路径异常或重复，请使用平台模板");
                long expanded = 0;
                var crc = new CRC32();
                int size;
                try (var input = zip.getInputStream(entry)) {
                 while ((size = input.read(buffer)) != -1) {
                    expanded += size; total += size;
                    if (expanded > MAX_ENTRY || total > MAX_TOTAL)
                        throw invalid("Excel解压内容过大，请移除图片及无关内容后使用平台模板重试");
                    crc.update(buffer, 0, size);
                 }
                }
                if (entry.getSize() != expanded || entry.getCrc() != crc.getValue())
                    throw invalid("Excel内部文件校验失败，请使用平台模板");
                if (lower.endsWith(".xml") || lower.endsWith(".rels")) {
                    try (var input = zip.getInputStream(entry)) { checkXml(input); }
                }
            }
           }
          } finally {
            Files.deleteIfExists(temporary);
          }
        } catch (IOException | IllegalArgumentException e) {
            var error = invalid("Excel压缩内容损坏或加密，请使用未加密的平台模板");
            error.initCause(e);
            throw error;
        }
        if (!names.contains("[Content_Types].xml") || !names.contains("xl/workbook.xml"))
            throw invalid("文件不是标准.xlsx工作簿，请使用平台模板");
    }

    private static void checkXml(java.io.InputStream input) {
        try {
            var factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.setProperty("jdk.xml.maxElementDepth", "100");
            parser.parse(input, new DefaultHandler() {
                @Override public void startElement(String uri, String local, String qualified, Attributes attributes) {
                    if ((uri.contains("spreadsheetml") && (local.equals("f") || local.equals("definedName")))
                            || local.equals("externalReference"))
                        throw invalid("询价模板不能包含公式或命名表达式，请复制并粘贴为值后导入");
                    if (local.equals("Relationship") && "external".equalsIgnoreCase(attributes.getValue("TargetMode")))
                        throw invalid("询价模板不能包含外部链接，请移除链接后导入");
                    String contentType = attributes.getValue("ContentType");
                    if (contentType != null) {
                        String type = contentType.toLowerCase(Locale.ROOT);
                        if (type.contains("macroenabled") || type.contains("vbaproject") || type.contains("activex"))
                            throw invalid("询价模板不能包含宏或活动控件，请使用平台.xlsx模板");
                    }
                }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                    throw new SAXException("External resource forbidden");
                }
            });
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException e) {
            throw invalid("Excel XML内容无效或含不允许的DTD/实体，请使用平台模板");
        }
    }
}
