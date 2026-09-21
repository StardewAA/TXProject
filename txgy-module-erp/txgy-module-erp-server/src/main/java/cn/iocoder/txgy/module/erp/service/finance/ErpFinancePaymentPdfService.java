package cn.iocoder.txgy.module.erp.service.finance;

import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.payment.ErpFinancePaymentRespVO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 付款单 PDF 导出服务。
 *
 * <p>PDF 直接从付款单详情生成，确保付款流水和关联订单明细同时导出。</p>
 */
@Service
public class ErpFinancePaymentPdfService {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 42;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final float BODY_SIZE = 9;
    private static final float LABEL_SIZE = 8;
    private static final float LINE_HEIGHT = 16;
    private static final float DETAIL_ROW_HEIGHT = 24;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generate(ErpFinancePaymentRespVO payment) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                float y = drawHeader(stream, font);
                y = drawSummary(stream, font, payment, y);
                y = drawDetailTitle(stream, font, y);
                drawDetailHeader(stream, font, y);
                y -= 22;

                int itemIndex = 1;
                List<ErpFinancePaymentRespVO.Item> items = payment.getItems();
                if (items != null) {
                    for (ErpFinancePaymentRespVO.Item item : items) {
                        if (y < MARGIN + 56) {
                            drawFooter(stream, font, payment, MARGIN + 12);
                            stream.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            stream = new PDPageContentStream(document, page);
                            y = drawHeader(stream, font);
                            y = drawDetailTitle(stream, font, y);
                            drawDetailHeader(stream, font, y);
                            y -= 22;
                        }
                        drawDetailRow(stream, font, item, itemIndex++, y);
                        y -= DETAIL_ROW_HEIGHT;
                    }
                }
                drawFooter(stream, font, payment, MARGIN + 18);
            } finally {
                stream.close();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        ClassPathResource fontResource = new ClassPathResource("fonts/OPPOSANS-M-subfont.ttf");
        if (fontResource.exists()) {
            try (InputStream inputStream = fontResource.getInputStream()) {
                return PDType0Font.load(document, inputStream);
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private float drawHeader(PDPageContentStream stream, PDFont font) throws IOException {
        float y = PAGE_HEIGHT - MARGIN;
        drawText(stream, font, 18, "付款流水", MARGIN, y);
        drawText(stream, font, LABEL_SIZE, "ERP 财务管理", PAGE_WIDTH - MARGIN - 70, y + 2);
        drawLine(stream, MARGIN, y - 10, PAGE_WIDTH - MARGIN, y - 10, 1.2f);
        return y - 28;
    }

    private float drawSummary(PDPageContentStream stream, PDFont font,
                              ErpFinancePaymentRespVO payment, float y) throws IOException {
        float boxHeight = 148;
        drawRect(stream, MARGIN, y - boxHeight, CONTENT_WIDTH, boxHeight, 0.8f);
        float left = MARGIN + 14;
        float right = MARGIN + CONTENT_WIDTH / 2 + 10;
        float row = y - 22;
        drawText(stream, font, 10, "付款单信息", left, row);
        row -= 24;
        drawSummaryField(stream, font, left, row, "付款单号", value(payment.getNo()));
        drawSummaryField(stream, font, right, row, "付款状态", statusName(payment.getStatus()));
        row -= LINE_HEIGHT;
        drawSummaryField(stream, font, left, row, "付款时间", formatDate(payment.getPaymentTime()));
        drawSummaryField(stream, font, right, row, "供应商", value(payment.getSupplierName()));
        row -= LINE_HEIGHT;
        drawSummaryField(stream, font, left, row, "付款账户", value(payment.getAccountName()));
        drawSummaryField(stream, font, right, row, "财务人员", value(payment.getFinanceUserName()));
        row -= LINE_HEIGHT;
        drawSummaryField(stream, font, left, row, "合计金额", money(payment.getTotalPrice()));
        drawSummaryField(stream, font, right, row, "优惠金额", money(payment.getDiscountPrice()));
        row -= LINE_HEIGHT;
        drawSummaryField(stream, font, left, row, "实际付款", money(payment.getPaymentPrice()));
        drawSummaryField(stream, font, right, row, "创建时间", formatDate(payment.getCreateTime()));
        row -= LINE_HEIGHT;
        drawSummaryField(stream, font, left, row, "创建人", value(payment.getCreatorName()));
        drawSummaryField(stream, font, right, row, "备注", value(payment.getRemark()));
        return y - boxHeight - 18;
    }

    private void drawSummaryField(PDPageContentStream stream, PDFont font, float x, float y,
                                  String label, String value) throws IOException {
        drawText(stream, font, LABEL_SIZE, label + "：", x, y);
        drawText(stream, font, BODY_SIZE, fit(value, 24), x + 56, y);
    }

    private float drawDetailTitle(PDPageContentStream stream, PDFont font, float y) throws IOException {
        drawText(stream, font, 10, "关联订单明细", MARGIN, y);
        return y - 18;
    }

    private void drawDetailHeader(PDPageContentStream stream, PDFont font, float y) throws IOException {
        float height = 22;
        drawRect(stream, MARGIN, y - height, CONTENT_WIDTH, height, 0.8f);
        drawText(stream, font, LABEL_SIZE, "序号", MARGIN + 8, y - 15);
        drawText(stream, font, LABEL_SIZE, "业务类型", MARGIN + 40, y - 15);
        drawText(stream, font, LABEL_SIZE, "订单号", MARGIN + 95, y - 15);
        drawText(stream, font, LABEL_SIZE, "应付金额", MARGIN + 235, y - 15);
        drawText(stream, font, LABEL_SIZE, "已付金额", MARGIN + 315, y - 15);
        drawText(stream, font, LABEL_SIZE, "本次付款", MARGIN + 395, y - 15);
        drawText(stream, font, LABEL_SIZE, "备注", MARGIN + 470, y - 15);
    }

    private void drawDetailRow(PDPageContentStream stream, PDFont font,
                               ErpFinancePaymentRespVO.Item item, int index, float y) throws IOException {
        float textY = y - 15;
        drawText(stream, font, BODY_SIZE, String.valueOf(index), MARGIN + 8, textY);
        drawText(stream, font, BODY_SIZE, bizTypeName(item.getBizType()), MARGIN + 40, textY);
        drawText(stream, font, BODY_SIZE, fit(item.getBizNo(), 22), MARGIN + 95, textY);
        drawText(stream, font, BODY_SIZE, money(item.getTotalPrice()), MARGIN + 235, textY);
        drawText(stream, font, BODY_SIZE, money(item.getPaidPrice()), MARGIN + 315, textY);
        drawText(stream, font, BODY_SIZE, money(item.getPaymentPrice()), MARGIN + 395, textY);
        drawText(stream, font, BODY_SIZE, fit(item.getRemark(), 6), MARGIN + 470, textY);
        drawLine(stream, MARGIN, y - DETAIL_ROW_HEIGHT, PAGE_WIDTH - MARGIN, y - DETAIL_ROW_HEIGHT, 0.5f);
    }

    private void drawFooter(PDPageContentStream stream, PDFont font,
                            ErpFinancePaymentRespVO payment, float y) throws IOException {
        drawText(stream, font, LABEL_SIZE, "本凭证由 ERP 付款单自动生成", MARGIN, y);
        drawText(stream, font, LABEL_SIZE, "付款单号：" + value(payment.getNo()),
                PAGE_WIDTH - MARGIN - 120, y);
    }

    private void drawText(PDPageContentStream stream, PDFont font, float size,
                          String text, float x, float y) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(safeText(text));
        stream.endText();
    }

    private void drawLine(PDPageContentStream stream, float x1, float y1,
                          float x2, float y2, float lineWidth) throws IOException {
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private void drawRect(PDPageContentStream stream, float x, float y, float width,
                          float height, float lineWidth) throws IOException {
        stream.setLineWidth(lineWidth);
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private String statusName(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "草稿";
            case 10 -> "审批中";
            case 20 -> "审批通过";
            case 30 -> "审批驳回";
            case 40 -> "已撤销";
            default -> "未知";
        };
    }

    private String bizTypeName(Integer bizType) {
        return switch (bizType == null ? -1 : bizType) {
            case 11 -> "采购入库";
            case 12 -> "采购退货";
            default -> "其他业务";
        };
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    private String money(BigDecimal value) {
        return value == null ? "CNY 0.00"
                : "CNY " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String fit(String value, int maxLength) {
        String text = value(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "...";
    }

    private String safeText(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }

}
