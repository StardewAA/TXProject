package cn.iocoder.txgy.module.erp.service.finance;

import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.payment.ErpFinancePaymentRespVO;
import cn.iocoder.txgy.module.erp.service.export.ErpDocumentFormPdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 付款单 PDF 导出器。
 *
 * <p>款项明细按「单据编号 / 应付金额 / 已付金额 / 本次付款 / 备注」列出，末尾小计，底部签批栏留空。</p>
 */
public final class ErpFinancePaymentPdfExporter {

    private static final String FILE_NAME = "付款单.pdf";
    private static final String TITLE = "付款单";
    private static final String[] HEADERS = {"序号", "单据编号", "应付金额", "已付金额", "本次付款", "备注"};
    private static final float[] WIDTHS = {0.8f, 2.4f, 1.4f, 1.4f, 1.4f, 1.8f};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private ErpFinancePaymentPdfExporter() {
    }

    public static void write(HttpServletResponse response, List<ErpFinancePaymentRespVO> payments)
            throws IOException {
        List<ErpDocumentFormPdfWriter.Form> forms = new ArrayList<>();
        if (payments != null) {
            for (ErpFinancePaymentRespVO payment : payments) {
                forms.add(toForm(payment));
            }
        }
        ErpDocumentFormPdfWriter.writeAll(response, FILE_NAME, forms);
    }

    private static ErpDocumentFormPdfWriter.Form toForm(ErpFinancePaymentRespVO payment) {
        List<String[]> rows = new ArrayList<>();
        if (payment.getItems() != null) {
            int index = 1;
            for (ErpFinancePaymentRespVO.Item item : payment.getItems()) {
                rows.add(new String[]{
                        String.valueOf(index++),
                        text(item.getBizNo()),
                        amount(item.getTotalPrice()),
                        amount(item.getPaidPrice()),
                        amount(item.getPaymentPrice()),
                        text(item.getRemark())
                });
            }
        }
        return new ErpDocumentFormPdfWriter.Form(TITLE, text(payment.getNo()),
                formatDate(payment.getPaymentTime()),
                "供应商：" + text(payment.getSupplierName()),
                HEADERS, WIDTHS,
                rows, amount(payment.getPaymentPrice()));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }
}
