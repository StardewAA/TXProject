package cn.iocoder.txgy.module.erp.service.sale;

import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.order.ErpSaleOrderRespVO;
import cn.iocoder.txgy.module.erp.service.export.ErpDocumentFormPdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售订单 PDF 导出器。
 *
 * <p>与采购订单同版式：序号 / 名称 / 单位 / 数量 / 单价 / 金额 / 备注，末尾小计，底部签批栏留空。</p>
 */
public final class ErpSaleOrderPdfExporter {

    private static final String FILE_NAME = "销售订单.pdf";
    private static final String TITLE = "销售订单";
    private static final String[] HEADERS = {"序号", "名称", "单位", "数量", "单价", "金额", "备注"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    /** 服务类订单（加工/施工服务）明细的计量单位。 */
    private static final String SERVICE_UNIT = "项";

    private ErpSaleOrderPdfExporter() {
    }

    public static void write(HttpServletResponse response, List<ErpSaleOrderRespVO> orders)
            throws IOException {
        List<ErpDocumentFormPdfWriter.Form> forms = new ArrayList<>();
        if (orders != null) {
            for (ErpSaleOrderRespVO order : orders) {
                forms.add(toForm(order));
            }
        }
        ErpDocumentFormPdfWriter.writeAll(response, FILE_NAME, forms);
    }

    private static ErpDocumentFormPdfWriter.Form toForm(ErpSaleOrderRespVO order) {
        List<String[]> rows = new ArrayList<>();
        String projectName = serviceProjectName(order);
        if (projectName != null) {
            // 服务类订单：整单就是一笔加工/施工服务，按项目名称出具一行「项」，金额取订单未税总额
            rows.add(new String[]{"1", projectName, SERVICE_UNIT, "1",
                    amount(order.getTotalProductPrice()), amount(order.getTotalProductPrice()), ""});
        } else if (order.getItems() != null) {
            int index = 1;
            for (ErpSaleOrderRespVO.Item item : order.getItems()) {
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
                "客户：" + text(order.getCustomerName()),
                HEADERS, ErpDocumentFormPdfWriter.PURCHASE_FORM_WIDTHS,
                rows, amount(order.getTotalProductPrice()));
    }

    /**
     * 服务类订单的项目名称。
     *
     * <p>销售订单表没有独立的项目名称/订单类型字段，服务类订单在库里表现为「整单仅一行汇总」：
     * 明细只有一行、数量为 1、且该行单价等于订单未税总额。此时以订单备注（项目名称）作为
     * 明细名称出具服务行，避免把挂在明细上的物料当成真正的销售物料。</p>
     */
    private static String serviceProjectName(ErpSaleOrderRespVO order) {
        String remark = order.getRemark();
        List<ErpSaleOrderRespVO.Item> items = order.getItems();
        if (remark == null || remark.isBlank() || items == null || items.size() != 1) {
            return null;
        }
        ErpSaleOrderRespVO.Item item = items.get(0);
        if (item.getCount() == null || item.getCount().compareTo(BigDecimal.ONE) != 0
                || item.getProductPrice() == null || order.getTotalProductPrice() == null) {
            return null;
        }
        return item.getProductPrice().compareTo(order.getTotalProductPrice()) == 0 ? remark.trim() : null;
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
