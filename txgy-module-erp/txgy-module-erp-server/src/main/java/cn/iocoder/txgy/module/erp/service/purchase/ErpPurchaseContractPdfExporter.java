package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.util.http.HttpUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 按企业采购合同模板生成可动态分页的正式合同 PDF。 */
public final class ErpPurchaseContractPdfExporter {
    private static final String FONT = "/fonts/OPPOSANS-M-subfont.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private ErpPurchaseContractPdfExporter() {}

    public static void write(HttpServletResponse response, ErpPurchaseContractDO contract,
                             ErpPurchaseOrderDO order, List<ErpPurchaseOrderItemDO> items,
                             Map<Long, ErpProductRespVO> products, ErpSupplierDO supplier,
                             String signerName) throws IOException {
        response.setHeader("Content-Disposition", "attachment;filename="
                + HttpUtils.encodeUtf8("采购合同-" + contract.getNo() + ".pdf"));
        response.setContentType("application/pdf");
        Document document = new Document(PageSize.A4, 52, 52, 44, 44);
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();
            BaseFont bf = font();
            Font title = new Font(bf, 20, Font.BOLD);
            Font heading = new Font(bf, 13, Font.BOLD);
            Font body = new Font(bf, 10.5f, Font.NORMAL);
            Font small = new Font(bf, 9, Font.NORMAL);

            Paragraph p = new Paragraph("采购合同", title);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(18);
            document.add(p);
            if (!Integer.valueOf(20).equals(contract.getStatus())) {
                Paragraph draft = new Paragraph("草稿（未审批）", new Font(bf, 11, Font.BOLD));
                draft.setAlignment(Element.ALIGN_CENTER);
                draft.setSpacingAfter(8);
                document.add(draft);
            }
            addLine(document, body, "合同编号：" + blank(contract.getNo()));
            addLine(document, body, "签订地点：" + blank(contract.getSignPlace()));
            addLine(document, body, "签订时间：" + date(contract.getSignDate()));
            addLine(document, body, "采购人（甲方）：" + blank(contract.getBuyerName()));
            addLine(document, body, "供应商（乙方）：" + first(contract.getSupplierNameSnapshot(), supplier == null ? null : supplier.getName()));
            addLine(document, body, "关联采购订单：" + blank(contract.getPurchaseOrderNo()));

            addHeading(document, heading, "一、合同货物");
            PdfPTable goods = new PdfPTable(new float[]{0.7f, 2.2f, 1.5f, 0.8f, 0.9f, 1.2f, 1.3f, 1.2f});
            goods.setWidthPercentage(100);
            goods.setHeaderRows(1);
            for (String h : new String[]{"序号", "货物名称", "规格型号", "单位", "数量", "单价（元）", "总价（元）", "交货期"}) {
                cell(goods, h, small, true);
            }
            int index = 1;
            for (ErpPurchaseOrderItemDO item : items) {
                ErpProductRespVO product = products.get(item.getProductId());
                cell(goods, String.valueOf(index++), small, false);
                cell(goods, product == null ? "" : product.getName(), small, false);
                cell(goods, product == null ? "" : product.getStandard(), small, false);
                cell(goods, product == null ? "" : product.getUnitName(), small, false);
                cell(goods, number(item.getCount()), small, false);
                cell(goods, number(item.getProductPrice()), small, false);
                cell(goods, number(item.getTotalPrice()), small, false);
                cell(goods, date(contract.getDeliveryDeadline()), small, false);
            }
            if (items.isEmpty()) {
                for (int i = 0; i < 8; i++) cell(goods, "", small, false);
            }
            goods.setSpacingAfter(10);
            document.add(goods);

            addHeading(document, heading, "二、合同总价");
            BigDecimal total = contract.getTotalAmount() == null ? BigDecimal.ZERO : contract.getTotalAmount();
            addBody(document, body, "合同总价为人民币大写：" + chineseMoney(total) + "，即 RMB￥"
                    + number(total) + "元。币种：" + blank(contract.getCurrency()) + "。");

            addHeading(document, heading, "三、质量要求");
            addBody(document, body, "货物应为全新、权属清晰且符合国家、行业及约定标准。质量标准："
                    + blank(contract.getQualityStandard()) + "。");

            addHeading(document, heading, "四、交货及验收");
            addBody(document, body, "乙方应于" + date(contract.getDeliveryDeadline()) + "前将货物交付至"
                    + blank(contract.getDeliveryPlace()) + "。甲方在到货后"
                    + blank(contract.getAcceptancePeriodDays()) + "日内完成验收；未填写的验收细项由双方另行确认。");

            addHeading(document, heading, "五、付款方式");
            addBody(document, body, payment(contract, total));
            if (!isBlank(contract.getPaymentTerms())) addBody(document, body, "其他付款条件：" + contract.getPaymentTerms());

            addHeading(document, heading, "六、售后服务");
            addBody(document, body, "质保期限为验收合格后" + blank(contract.getWarrantyPeriodMonths())
                    + "个月；乙方在收到通知后" + blank(contract.getAfterSaleResponseHours())
                    + "小时内响应，并在" + blank(contract.getOnSiteServiceHours()) + "小时内到场处理。"
                    + "同一问题最多维修" + blank(contract.getMaxRepairTimes()) + "次。");

            addHeading(document, heading, "七、违约责任");
            addBody(document, body, "甲方无正当理由拒收货物的，按合同总价的"
                    + percent(contract.getBuyerRejectPenaltyRatio()) + "承担违约责任；逾期付款每日按"
                    + perDay(contract.getBuyerOverdueRate()) + "计收，超过"
                    + blank(contract.getBuyerTerminationDays()) + "日，乙方可解除合同。");
            addBody(document, body, "乙方交付货物质量不合格的，按合同总价的"
                    + percent(contract.getSellerQualityPenaltyRatio()) + "承担违约责任；逾期交货每日按"
                    + perDay(contract.getSellerOverdueRate()) + "计收，超过"
                    + blank(contract.getSellerTerminationDays()) + "日，甲方可解除合同，并按合同总价的"
                    + percent(contract.getSellerTerminationCompensationRatio()) + "承担赔偿责任。权利或知识产权瑕疵违约金比例为"
                    + percent(contract.getIpDefectPenaltyRatio()) + "。");

            addHeading(document, heading, "八、争议解决办法");
            addBody(document, body, "合同履行期间发生争议，双方应先行协商；协商不成的，依法维护各自合法权益。");

            addHeading(document, heading, "九、其他");
            addBody(document, body, isBlank(contract.getLegalTerms())
                    ? "未尽事宜由双方依法另行签订补充协议。本合同经双方签章后生效。"
                    : contract.getLegalTerms());

            PdfPTable signatures = new PdfPTable(new float[]{1f, 1f});
            signatures.setWidthPercentage(100);
            signatures.setKeepTogether(true);
            signatures.setSpacingBefore(14);
            String buyer = party("甲方", contract.getBuyerName(), contract.getBuyerLegalRepresentative(),
                    contract.getBuyerAddress(), contract.getBuyerBankName(), contract.getBuyerBankAccount(),
                    contract.getBuyerPhone(), contract.getBuyerFax(), signerName, contract.getSignDate());
            String seller = party("乙方", first(contract.getSupplierNameSnapshot(), supplier == null ? null : supplier.getName()),
                    contract.getSellerLegalRepresentative(), contract.getSellerAddress(), contract.getSellerBankName(),
                    contract.getSellerBankAccount(), contract.getSellerPhone(), contract.getSellerFax(), null, contract.getSignDate());
            signatureCell(signatures, buyer, body);
            signatureCell(signatures, seller, body);
            document.add(signatures);
        } catch (DocumentException e) {
            throw new IOException("采购合同 PDF 生成失败", e);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    private static String payment(ErpPurchaseContractDO c, BigDecimal total) {
        return "预付款" + percent(c.getAdvancePaymentRatio()) + "，金额￥" + number(amount(total, c.getAdvancePaymentRatio()))
                + "元，于合同生效后" + blank(c.getAdvancePaymentDays()) + "日内支付；验收款"
                + percent(c.getAcceptancePaymentRatio()) + "，金额￥" + number(amount(total, c.getAcceptancePaymentRatio()))
                + "元，于验收合格后" + blank(c.getAcceptancePaymentDays()) + "日内支付；质保金"
                + percent(c.getWarrantyRetentionRatio()) + "，金额￥" + number(amount(total, c.getWarrantyRetentionRatio()))
                + "元，于" + blank(c.getWarrantyReleaseDays()) + "日内按“"
                + blank(c.getWarrantyReleaseCondition()) + "”释放。";
    }

    private static BigDecimal amount(BigDecimal total, BigDecimal ratio) {
        if (total == null || ratio == null) return null;
        return total.multiply(ratio).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static void addHeading(Document doc, Font font, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, font); p.setSpacingBefore(8); p.setSpacingAfter(4); doc.add(p);
    }
    private static void addLine(Document doc, Font font, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, font); p.setLeading(17); doc.add(p);
    }
    private static void addBody(Document doc, Font font, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, font); p.setFirstLineIndent(21); p.setLeading(19); p.setSpacingAfter(3); doc.add(p);
    }
    private static void cell(PdfPTable table, String text, Font font, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(blank(text), font));
        c.setPadding(4); c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (header) c.setBackgroundColor(new Color(235, 235, 235));
        table.addCell(c);
    }
    private static void signatureCell(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font)); c.setPadding(10); c.setLeading(4, 1.3f); table.addCell(c);
    }

    private static String party(String label, String name, String legal, String address, String bank,
                                String account, String phone, String fax, String signer, LocalDateTime date) {
        return label + "（盖章）：" + blank(name) + "\n法定代表人（授权代表）：" + first(signer, legal)
                + "\n地址：" + blank(address) + "\n开户行：" + blank(bank) + "\n账号：" + blank(account)
                + "\n电话：" + blank(phone) + "\n传真：" + blank(fax) + "\n签约日期：" + date(date);
    }

    private static BaseFont font() throws IOException, DocumentException {
        try (InputStream in = ErpPurchaseContractPdfExporter.class.getResourceAsStream(FONT)) {
            if (in == null) throw new IOException("采购合同中文字体不存在");
            return BaseFont.createFont("contract-font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }

    private static String date(LocalDateTime value) { return value == null ? "________" : DATE.format(value); }
    private static String number(BigDecimal value) { return value == null ? "________" : value.stripTrailingZeros().toPlainString(); }
    private static String percent(BigDecimal value) { return value == null ? "____%" : number(value) + "%"; }
    private static String perDay(BigDecimal value) { return value == null ? "____/日" : number(value) + "‰/日"; }
    private static String blank(Object value) { return value == null || String.valueOf(value).isBlank() ? "________" : String.valueOf(value); }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private static String first(String first, String second) { return !isBlank(first) ? first : blank(second); }

    /** 人民币大写，覆盖合同金额常用范围。 */
    private static String chineseMoney(BigDecimal input) {
        if (input == null) return "________";
        String[] digit = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] unit = {"分", "角", "元", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟"};
        long fen = input.abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
        if (fen == 0) return "零元整";
        String raw = Long.toString(fen);
        StringBuilder out = new StringBuilder();
        boolean zero = false;
        for (int i = 0; i < raw.length(); i++) {
            int n = raw.charAt(raw.length() - 1 - i) - '0';
            if (n == 0) {
                zero = out.length() > 0;
                if (i == 2 || i == 6 || i == 10) out.insert(0, unit[i]);
            } else {
                out.insert(0, digit[n] + unit[i]);
                if (zero) out.insert(0, "零");
                zero = false;
            }
        }
        String result = out.toString().replaceAll("零+", "零").replace("零万", "万").replace("零亿", "亿");
        if (fen % 100 == 0) result += "整";
        return input.signum() < 0 ? "负" + result : result;
    }
}
