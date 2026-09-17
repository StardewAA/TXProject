package cn.iocoder.txgy.module.crm.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX 表格读取工具
 *
 * <p>.docx 本质是一个 zip，正文在 {@code word/document.xml}。
 * 本工具只依赖 JDK 自带的 XML / ZIP 能力（不引入 Apache POI），
 * 用于把文档里的表格读成「表格 -> 行 -> 单元格文本」的三层结构。
 *
 * <p>说明：
 * 1. 只读取表格内容，忽略表格之外的段落（供应商导入场景不关心无关正文）；
 * 2. 单元格内的多个段落 / 换行会被保留为 \n；
 * 3. 嵌套表格不单独返回（其文本会包含在外层单元格里），避免数据重复。
 *
 * @author 防腐保温项目组
 */
@Slf4j
public class DocxTableUtils {

    /**
     * WordprocessingML 命名空间
     */
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /**
     * 正文在 docx（zip）中的路径
     */
    private static final String DOCUMENT_XML = "word/document.xml";

    private DocxTableUtils() {
    }

    /**
     * 读取 docx 中的全部表格
     *
     * @param content docx 文件字节
     * @return 表格列表：表格 -> 行 -> 单元格文本
     * @throws Exception 文件不是合法 docx、或 XML 解析失败时抛出
     */
    public static List<List<List<String>>> readTables(byte[] content) throws Exception {
        byte[] documentXml = readZipEntry(content, DOCUMENT_XML);
        if (documentXml == null) {
            throw new IllegalArgumentException("缺少 word/document.xml，不是有效的 .docx 文件");
        }
        Document document = parse(documentXml);
        List<List<List<String>>> tables = new ArrayList<>();
        NodeList tableNodes = document.getElementsByTagNameNS(W_NS, "tbl");
        for (int i = 0; i < tableNodes.getLength(); i++) {
            Element table = (Element) tableNodes.item(i);
            // 跳过嵌套表格：其内容已包含在外层表格的单元格文本里
            if (hasTableAncestor(table)) {
                continue;
            }
            List<List<String>> rows = readTable(table);
            if (!rows.isEmpty()) {
                tables.add(rows);
            }
        }
        return tables;
    }

    private static List<List<String>> readTable(Element table) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : childElements(table, "tr")) {
            List<String> cells = new ArrayList<>();
            for (Element tc : childElements(tr, "tc")) {
                cells.add(readCellText(tc).trim());
            }
            rows.add(cells);
        }
        return rows;
    }

    /**
     * 读取单元格文本：按文档顺序拼接所有 w:t，段落/换行转为 \n
     */
    private static String readCellText(Element cell) {
        StringBuilder sb = new StringBuilder();
        appendText(cell, sb, true);
        return sb.toString();
    }

    private static void appendText(Element element, StringBuilder sb, boolean root) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            String name = child.getLocalName();
            if ("t".equals(name)) {
                sb.append(child.getTextContent());
            } else if ("br".equals(name) || "cr".equals(name)) {
                appendNewLine(sb);
            } else if ("p".equals(name)) {
                if (!root) {
                    appendNewLine(sb);
                }
                appendText(child, sb, false);
            } else {
                appendText(child, sb, false);
            }
        }
    }

    private static void appendNewLine(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    /**
     * 获取直接子元素（按 localName 过滤）
     */
    private static List<Element> childElements(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /**
     * 是否位于另一个表格内部（即嵌套表格）
     */
    private static boolean hasTableAncestor(Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE && "tbl".equals(parent.getLocalName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    /**
     * 安全解析 XML（禁用外部实体，防御 XXE）
     */
    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (Exception e) {
            // 个别解析器不支持上述特性时忽略：secure processing 已足够
            log.debug("[parse][XML 安全特性设置失败，已忽略]", e);
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml));
    }

    /**
     * 从 zip 中读取指定条目内容
     */
    private static byte[] readZipEntry(byte[] content, String entryName) throws Exception {
        try (InputStream in = new ByteArrayInputStream(content);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zip.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    return out.toByteArray();
                }
            }
        }
        return null;
    }

}
