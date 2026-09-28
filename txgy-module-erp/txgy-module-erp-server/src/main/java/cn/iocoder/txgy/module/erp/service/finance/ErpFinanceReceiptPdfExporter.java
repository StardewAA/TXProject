package cn.iocoder.txgy.module.erp.service.finance;

import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.receipt.ErpFinanceReceiptRespVO;
import cn.iocoder.txgy.module.erp.service.export.ErpDocumentFormPdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 收款单 PDF 导出器。
 *
 * <p>款项明细按「单据编号 / 应收金额 / 已收金额 / 本次收款 / 备注」列出，末尾小计，底部签批栏留空。</p>
 */
public final class ErpFinanceReceiptPdfExporter {

    private static final String FILE_NAME = "收款单.pdf";
    private static final String TITLE = "收款单";
    private static final String[] HEADERS = {"序号", "单据编号", "应收金额", "已收金额", "本次收款", "备注"};
    private static final float[] WIDTHS = {0.8f, 2.4f, 1.4f, 1.4f, 1.4f, 1.8f};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private ErpFinanceReceiptPdfExporter() {
    }

    public static void write(HttpServletResponse response, List<ErpFinanceReceiptRespVO> receipts)
            throws IOException {
        List<ErpDocumentFormPdfWriter.Form> forms = new ArrayList<>();
        if (receipts != null) {
            for (ErpFinanceReceiptRespVO receipt : receipts) {
                forms.add(toForm(receipt));
            }
        }
        ErpDocumentFormPdfWriter.writeAll(response, FILE_NAME, forms);
    }

    private static ErpDocumentFormPdfWriter.Form toForm(ErpFinanceReceiptRespVO receipt) {
        List<String[]> rows = new ArrayList<>();
        if (receipt.getItems() != null) {
            int index = 1;
            for (ErpFinanceReceiptRespVO.Item item : receipt.getItems()) {
                rows.add(new String[]{
                        String.valueOf(index++),
                        text(item.getBizNo()),
                        amount(item.getTotalPrice()),
                        amount(item.getReceiptedPrice()),
                        amount(item.getReceiptPrice()),
                        text(item.getRemark())
                });
            }
        }
        return new ErpDocumentFormPdfWriter.Form(TITLE, text(receipt.getNo()),
                formatDate(receipt.getReceiptTime()),
                "客户：" + text(receipt.getCustomerName()),
                HEADERS, WIDTHS,
                rows, amount(receipt.getReceiptPrice()));
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
