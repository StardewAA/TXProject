package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderRespVO;
import cn.iocoder.txgy.module.erp.service.export.ErpDocumentFormPdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单 PDF 导出器。
 *
 * <p>按企业纸质「物品申购单」版式输出：序号 / 名称 / 单位 / 数量 / 单价 / 金额 / 备注，
 * 末尾小计，底部签批栏留空。选中多条时每条占一页。</p>
 */
public final class ErpPurchaseOrderPdfExporter {

    private static final String FILE_NAME = "采购订单.pdf";
    private static final String TITLE = "采购订单";
    private static final String[] HEADERS = {"序号", "名称", "单位", "数量", "单价", "金额", "备注"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private ErpPurchaseOrderPdfExporter() {
    }

    public static void write(HttpServletResponse response, List<ErpPurchaseOrderRespVO> orders)
            throws IOException {
        List<ErpDocumentFormPdfWriter.Form> forms = new ArrayList<>();
        if (orders != null) {
            for (ErpPurchaseOrderRespVO order : orders) {
                forms.add(toForm(order));
            }
        }
        ErpDocumentFormPdfWriter.writeAll(response, FILE_NAME, forms);
    }

    private static ErpDocumentFormPdfWriter.Form toForm(ErpPurchaseOrderRespVO order) {
        List<String[]> rows = new ArrayList<>();
        if (order.getItems() != null) {
            int index = 1;
            for (ErpPurchaseOrderRespVO.Item item : order.getItems()) {
                rows.add(new String[]{
                        String.valueOf(index++),
                        text(item.getProductName()),
                        text(item.getProductUnitName()),
                        amount(item.getCount()),
                        amount(item.getProductPrice()),
                        amount(lineAmount(item.getProductPrice(), item.getCount())),
                        text(item.getRemark())
                });
            }
        }
        return new ErpDocumentFormPdfWriter.Form(TITLE, text(order.getNo()),
                formatDate(order.getOrderTime()),
                "供应商：" + text(order.getSupplierName()),
                HEADERS, ErpDocumentFormPdfWriter.PURCHASE_FORM_WIDTHS,
                rows, amount(order.getTotalProductPrice()));
    }

    /** 行金额 = 单价 × 数量。 */
    private static BigDecimal lineAmount(BigDecimal price, BigDecimal count) {
        return price == null || count == null ? null : price.multiply(count);
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
