package cn.iocoder.txgy.module.erp.service.export;

import cn.iocoder.txgy.framework.common.util.http.HttpUtils;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.GrayColor;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ERP 单据表单 PDF 写入器。
 *
 * <p>按企业纸质单据（物品申购单）版式输出：纵向 A4，页眉 LOGO + 红色横线，
 * 标题、日期与编号行，明细表（表头与列宽可配置），小计行，底部签批栏（留空待签），
 * 页脚为公司页脚图。页眉页脚尺寸与 {@link ErpPdfExportUtils} 保持一致。</p>
 *
 * <p>选中多条单据时，每条单据占一页。</p>
 */
public final class ErpDocumentFormPdfWriter {

    private static final String HEADER_LOGO_RESOURCE = "/pdf/tianxin-pipe-industry-header-logo.jpeg";
    private static final String FOOTER_RESOURCE = "/pdf/tianxin-pipe-industry-footer-cropped.png";
    /** 随包提供的中文字体，容器内无系统 CJK 字体时使用。 */
    private static final String BUNDLED_FONT_RESOURCE = "/fonts/OPPOSANS-M-subfont.ttf";

    private static final float PAGE_MARGIN = 42f;
    private static final float HEADER_TOP_MARGIN = 72f;
    private static final float FOOTER_BOTTOM_MARGIN = 60f;
    private static final float FOOTER_MAX_WIDTH_RATIO = 0.90f;
    private static final float FOOTER_MAX_HEIGHT = 42f;

    /** 明细表默认列宽比例，取自纸质「物品申购单」模板的 7 列网格。 */
    public static final float[] PURCHASE_FORM_WIDTHS =
            {1396f, 1590f, 940f, 1030f, 1482f, 1518f, 1258f};

    private ErpDocumentFormPdfWriter() {
    }

    /** 一张单据表单的数据。 */
    public record Form(String title, String no, String dateText, String extra,
                       String[] headers, float[] widths,
                       List<String[]> rows, String total) {
    }

    public static void write(HttpServletResponse response, String fileName, Form form)
            throws IOException {
        writeAll(response, fileName, List.of(form));
    }

    public static void writeAll(HttpServletResponse response, String fileName, List<Form> forms)
            throws IOException {
        response.setHeader("Content-Disposition",
                "attachment;filename=" + HttpUtils.encodeUtf8(fileName));
        response.setContentType("application/pdf");

        Document document = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN,
                HEADER_TOP_MARGIN, FOOTER_BOTTOM_MARGIN);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
            writer.setPageEvent(new BrandingPageEvent(loadHeaderLogo(), loadFooter()));
            document.open();

            BaseFont baseFont = createChineseFont();
            Font titleFont = new Font(baseFont, 16, Font.BOLD);
            Font metaFont = new Font(baseFont, 9, Font.NORMAL);
            Font headerFont = new Font(baseFont, 9, Font.BOLD);
            Font cellFont = new Font(baseFont, 9, Font.NORMAL);

            if (forms.isEmpty()) {
                // 空结果也要产出合法 PDF（否则 iText 在无页文档上 close 会抛异常）
                Paragraph empty = new Paragraph("暂无符合条件的数据", metaFont);
                empty.setSpacingBefore(120);
                empty.setAlignment(Element.ALIGN_CENTER);
                document.add(empty);
                return;
            }

            for (int i = 0; i < forms.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                addForm(document, forms.get(i), baseFont, titleFont, metaFont, headerFont, cellFont);
            }
        } catch (DocumentException e) {
            throw new IOException("单据 PDF 生成失败", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private static void addForm(Document document, Form form, BaseFont baseFont,
                                Font titleFont, Font metaFont, Font headerFont, Font cellFont)
            throws DocumentException {
        Paragraph heading = new Paragraph(nullToEmpty(form.title()), titleFont);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(6);
        document.add(heading);

        Paragraph dateLine = new Paragraph("日期：" + nullToEmpty(form.dateText()), metaFont);
        dateLine.setAlignment(Element.ALIGN_RIGHT);
        dateLine.setSpacingAfter(2);
        document.add(dateLine);

        Paragraph noLine = new Paragraph("编号：" + nullToEmpty(form.no()), metaFont);
        noLine.setSpacingAfter(6);
        if (form.extra() != null && !form.extra().isBlank()) {
            // 编号左、往来单位右，同一行
            PdfPTable metaRow = new PdfPTable(new float[]{1f, 1f});
            metaRow.setWidthPercentage(100);
            PdfPCell left = new PdfPCell(new Phrase("编号：" + nullToEmpty(form.no()), metaFont));
            left.setBorder(0);
            left.setPadding(0);
            metaRow.addCell(left);
            PdfPCell right = new PdfPCell(new Phrase(form.extra(), metaFont));
            right.setBorder(0);
            right.setPadding(0);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            metaRow.addCell(right);
            metaRow.setSpacingAfter(6);
            document.add(metaRow);
        } else {
            document.add(noLine);
        }

        String[] headers = form.headers() == null ? new String[0] : form.headers();
        if (headers.length > 0) {
            int columnCount = headers.length;
            PdfPTable table = new PdfPTable(columnCount);
            table.setWidthPercentage(100);
            table.setWidths(resolveWidths(form.widths(), columnCount));
            table.setHeaderRows(1);

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(header), headerFont));
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBackgroundColor(new GrayColor(0.90f));
                table.addCell(cell);
            }

            List<String[]> rows = form.rows() == null ? new ArrayList<>() : form.rows();
            for (String[] row : rows) {
                for (int i = 0; i < columnCount; i++) {
                    String value = row != null && i < row.length ? row[i] : "";
                    PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(value), cellFont));
                    cell.setPadding(5);
                    cell.setMinimumHeight(20f);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    table.addCell(cell);
                }
            }

            if (form.total() != null && !form.total().isBlank()) {
                int labelSpan = Math.max(1, columnCount - 2);
                PdfPCell labelCell = new PdfPCell(new Phrase("小计", headerFont));
                labelCell.setColspan(labelSpan);
                labelCell.setPadding(5);
                labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(labelCell);

                PdfPCell valueCell = new PdfPCell(new Phrase(form.total(), headerFont));
                valueCell.setPadding(5);
                valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(valueCell);

                for (int i = labelSpan + 1; i < columnCount; i++) {
                    PdfPCell tail = new PdfPCell(new Phrase("", cellFont));
                    tail.setPadding(5);
                    table.addCell(tail);
                }
            }
            document.add(table);
        }

        document.add(buildSignatureTable(baseFont));
    }

    /** 签批栏：留空待签，与纸质模板的行结构一致。 */
    private static PdfPTable buildSignatureTable(BaseFont baseFont) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 1.6f, 1f, 1.6f});
        table.setWidthPercentage(100);
        Font labelFont = new Font(baseFont, 9, Font.NORMAL);

        addSignatureRow(table, labelFont, new String[]{"制单人", "", "审核人", ""});
        addSignatureRow(table, labelFont, new String[]{"部门主管意见", "", "部门经理", ""});

        PdfPCell label = new PdfPCell(new Phrase("总经理意见", labelFont));
        label.setPadding(6);
        label.setMinimumHeight(30f);
        label.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(label);
        PdfPCell blank = new PdfPCell(new Phrase("", labelFont));
        blank.setColspan(3);
        blank.setPadding(6);
        blank.setMinimumHeight(30f);
        table.addCell(blank);
        return table;
    }

    private static void addSignatureRow(PdfPTable table, Font font, String[] labels) {
        for (String text : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(text), font));
            cell.setPadding(6);
            cell.setMinimumHeight(30f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private static float[] resolveWidths(float[] widths, int columnCount) {
        if (widths != null && widths.length == columnCount) {
            return widths;
        }
        if (columnCount == PURCHASE_FORM_WIDTHS.length) {
            return PURCHASE_FORM_WIDTHS;
        }
        float[] equal = new float[columnCount];
        Arrays.fill(equal, 1f);
        return equal;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BaseFont createChineseFont() throws IOException, DocumentException {
        // 1. 优先使用随包提供的中文字体。
        //    运行镜像（eclipse-temurin:25-jre）只自带 DejaVu 等拉丁字体，
        //    一旦找不到 CJK 字体就回退到 Helvetica，所有中文会渲染成空白。
        try (InputStream inputStream = ErpDocumentFormPdfWriter.class
                .getResourceAsStream(BUNDLED_FONT_RESOURCE)) {
            if (inputStream != null) {
                byte[] fontBytes = inputStream.readAllBytes();
                try {
                    return BaseFont.createFont("OPPOSANS-M-subfont.ttf", BaseFont.IDENTITY_H,
                            BaseFont.EMBEDDED, true, fontBytes, null);
                } catch (DocumentException | IOException ignored) {
                    // 随包字体损坏时继续尝试系统字体
                }
            }
        }
        // 2. 回退到系统字体
        for (String fontPath : fontPaths()) {
            if (!Files.isRegularFile(Paths.get(fontPath))) {
                continue;
            }
            for (String candidate : Arrays.asList(fontPath, fontPath + ",0")) {
                try {
                    return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (DocumentException | IOException ignored) {
                    // 尝试下一个系统字体或 TTC 字体索引。
                }
            }
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private static Image loadHeaderLogo() throws IOException, DocumentException {
        try (InputStream inputStream = ErpDocumentFormPdfWriter.class
                .getResourceAsStream(HEADER_LOGO_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("单据 PDF 页眉资源不存在: " + HEADER_LOGO_RESOURCE);
            }
            Image logo = Image.getInstance(inputStream.readAllBytes());
            logo.scaleToFit(145f, 42f);
            return logo;
        }
    }

    private static Image loadFooter() throws IOException, DocumentException {
        try (InputStream inputStream = ErpDocumentFormPdfWriter.class
                .getResourceAsStream(FOOTER_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("单据 PDF 页脚资源不存在: " + FOOTER_RESOURCE);
            }
            Image footer = Image.getInstance(inputStream.readAllBytes());
            footer.scaleToFit(PageSize.A4.getWidth() * FOOTER_MAX_WIDTH_RATIO, FOOTER_MAX_HEIGHT);
            return footer;
        }
    }

    private static List<String> fontPaths() {
        String windowsDir = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
        return Arrays.asList(
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJKsc-Regular.otf",
                windowsDir + "\\Fonts\\msyh.ttc",
                windowsDir + "\\Fonts\\simsun.ttc"
        );
    }

    /** 与列表导出相同的页眉 LOGO、红色横线与页脚。 */
    private static final class BrandingPageEvent extends PdfPageEventHelper {

        private final Image headerLogo;
        private final Image footer;

        private BrandingPageEvent(Image headerLogo, Image footer) {
            this.headerLogo = headerLogo;
            this.footer = footer;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float pageHeight = document.getPageSize().getHeight();
            try {
                Image pageLogo = Image.getInstance(headerLogo);
                pageLogo.setAbsolutePosition(PAGE_MARGIN, pageHeight - 48f);
                canvas.addImage(pageLogo);

                Image pageFooter = Image.getInstance(footer);
                pageFooter.setAbsolutePosition(PAGE_MARGIN, 12f);
                canvas.addImage(pageFooter);
            } catch (DocumentException e) {
                throw new IllegalStateException("单据 PDF 页眉或页脚图片绘制失败", e);
            }

            canvas.saveState();
            canvas.setRGBColorStroke(220, 41, 51);
            canvas.setLineWidth(1.5f);
            canvas.moveTo(PAGE_MARGIN, pageHeight - 54f);
            canvas.lineTo(document.getPageSize().getWidth() - PAGE_MARGIN, pageHeight - 54f);
            canvas.stroke();
            canvas.restoreState();
        }
    }
}
