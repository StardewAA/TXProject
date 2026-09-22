package cn.iocoder.txgy.module.erp.service.export;

import cn.idev.excel.annotation.ExcelProperty;
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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * ERP 列表 PDF 导出工具。
 *
 * <p>导出列直接读取现有 VO 的 {@link ExcelProperty} 标注，保证 PDF 与 Excel
 * 使用同一套字段和表头，不改变现有业务查询及 Excel 导出逻辑。</p>
 */
public final class ErpPdfExportUtils {

    private static final String HEADER_LOGO_RESOURCE =
            "/pdf/tianxin-pipe-industry-header-logo.jpeg";
    private static final String FOOTER_RESOURCE =
            "/pdf/tianxin-pipe-industry-footer-cropped.png";
    /** 随包提供的中文字体，容器内无系统 CJK 字体时使用。 */
    private static final String BUNDLED_FONT_RESOURCE = "/fonts/OPPOSANS-M-subfont.ttf";
    private static final float PAGE_MARGIN = 24f;
    private static final float HEADER_TOP_MARGIN = 72f;
    private static final float FOOTER_BOTTOM_MARGIN = 60f;
    private static final float FOOTER_MAX_WIDTH_RATIO = 0.90f;
    private static final float FOOTER_MAX_HEIGHT = 42f;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private ErpPdfExportUtils() {
    }

    public static <T> void write(HttpServletResponse response, String fileName, String title,
                                 Class<T> dataType, List<T> data) throws IOException {
        response.setHeader("Content-Disposition",
                "attachment;filename=" + HttpUtils.encodeUtf8(fileName));
        response.setContentType("application/pdf");

        Document document = new Document(PageSize.A4.rotate(), PAGE_MARGIN, PAGE_MARGIN,
                HEADER_TOP_MARGIN, FOOTER_BOTTOM_MARGIN);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
            writer.setPageEvent(new BrandingPageEvent(loadHeaderLogo(), loadFooter()));
            document.open();

            BaseFont baseFont = createChineseFont();
            Font titleFont = new Font(baseFont, 14, Font.BOLD);
            Font headerFont = new Font(baseFont, 7, Font.BOLD);
            Font normalFont = new Font(baseFont, 7, Font.NORMAL);

            Paragraph heading = new Paragraph(title, titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingAfter(10);
            document.add(heading);

            List<ExportColumn> columns = getColumns(dataType);
            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            table.setWidths(calculateWidths(columns, data));
            table.setHeaderRows(1);

            for (ExportColumn column : columns) {
                addCell(table, column.header(), headerFont, true);
            }
            if (data != null) {
                for (T item : data) {
                    for (ExportColumn column : columns) {
                        addCell(table, format(readValue(column.field(), item)), normalFont, false);
                    }
                }
            }
            document.add(table);
        } catch (DocumentException e) {
            throw new IOException("ERP PDF 生成失败: " + title, e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private static List<ExportColumn> getColumns(Class<?> dataType) {
        List<ExportColumn> columns = new ArrayList<>();
        for (Field field : dataType.getDeclaredFields()) {
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null) {
                continue;
            }
            String header = property.value().length == 0 || property.value()[0].isBlank()
                    ? field.getName() : property.value()[0];
            field.setAccessible(true);
            columns.add(new ExportColumn(field, header));
        }
        return columns;
    }

    private static float[] calculateWidths(List<ExportColumn> columns, List<?> data) {
        float[] widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int width = displayLength(columns.get(i).header());
            if (data != null) {
                for (Object item : data) {
                    width = Math.max(width, Math.min(24, displayLength(format(readValue(
                            columns.get(i).field(), item)))));
                }
            }
            widths[i] = Math.max(1.4f, Math.min(7.5f, width / 2.2f));
        }
        return widths;
    }

    private static Object readValue(Field field, Object item) {
        if (item == null) {
            return null;
        }
        try {
            return field.get(item);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMATTER.format(dateTime);
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof LocalTime time) {
            return TIME_FORMATTER.format(time);
        }
        if (value instanceof Date date) {
            return DATE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(ErpPdfExportUtils::format).reduce((left, right) ->
                    left + "，" + right).orElse("");
        }
        if (value.getClass().isArray()) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) {
                    result.append('，');
                }
                result.append(format(Array.get(value, i)));
            }
            return result.toString();
        }
        return String.valueOf(value);
    }

    private static int displayLength(String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            length += value.charAt(i) > 255 ? 2 : 1;
        }
        return length;
    }

    private static void addCell(PdfPTable table, String value, Font font, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(header ? 4 : 3);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setNoWrap(false);
        if (header) {
            cell.setBackgroundColor(new com.lowagie.text.pdf.GrayColor(0.90f));
        }
        table.addCell(cell);
    }

    private static BaseFont createChineseFont() throws IOException, DocumentException {
        // 1. 优先使用随包提供的中文字体。
        //    运行镜像（eclipse-temurin:25-jre）只自带 DejaVu 等拉丁字体，
        //    一旦找不到 CJK 字体就回退到 Helvetica，所有中文会渲染成空白。
        try (InputStream inputStream = ErpPdfExportUtils.class
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
        try (InputStream inputStream = ErpPdfExportUtils.class
                .getResourceAsStream(HEADER_LOGO_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("ERP PDF 页眉资源不存在: " + HEADER_LOGO_RESOURCE);
            }
            Image logo = Image.getInstance(inputStream.readAllBytes());
            logo.scaleToFit(145f, 42f);
            return logo;
        }
    }

    private static Image loadFooter() throws IOException, DocumentException {
        try (InputStream inputStream = ErpPdfExportUtils.class
                .getResourceAsStream(FOOTER_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("ERP PDF 页脚资源不存在: " + FOOTER_RESOURCE);
            }
            Image footer = Image.getInstance(inputStream.readAllBytes());
            footer.scaleToFit(PageSize.A4.rotate().getWidth() * FOOTER_MAX_WIDTH_RATIO,
                    FOOTER_MAX_HEIGHT);
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

    private record ExportColumn(Field field, String header) {
    }

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
                throw new IllegalStateException("ERP PDF 页眉或页脚图片绘制失败", e);
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
