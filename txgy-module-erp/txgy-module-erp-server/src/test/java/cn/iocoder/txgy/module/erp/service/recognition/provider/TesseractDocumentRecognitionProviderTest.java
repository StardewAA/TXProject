package cn.iocoder.txgy.module.erp.service.recognition.provider;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TesseractDocumentRecognitionProviderTest {

    @Test
    void shouldParseSingleLineImageWithNumericProductCode() {
        String text = "供应商名称 订单日期 外部单号 产品编码 产品名称 数量 单价 税率(%) 备注\n"
                + "山东齐鲁石化防腐材料有限公司 2026-09-08 xd2011111111 21344444444 苹果 1 200 1 无";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals("山东齐鲁石化防腐材料有限公司", document.getSupplierName());
        assertEquals("2026-09-08", document.getDocumentDate());
        assertEquals("xd2011111111", document.getDocumentNo());
        assertEquals(1, document.getItems().size());
        assertEquals("21344444444", document.getItems().getFirst().getProductCode());
        assertEquals("苹果", document.getItems().getFirst().getProductName());
        assertEquals("1", document.getItems().getFirst().getTaxPercent().toPlainString());
        assertEquals("无", document.getItems().getFirst().getRemark());
        assertNull(document.getRemark());
    }

    @Test
    void shouldParseSingleLineImageWithPoAndAlphaNumericProductCode() {
        String text = "供应商名称 订单日期 外部单号 产品编码 产品名称 数量 单价 税率(%) 备注\n"
                + "测试供应商A 2026-09-09 PO-TEST-20260909-001 TEST-PIPE-001 测试防腐保温管 "
                + "10.00 128.50 13.00 Excel导入测试-第一条明细";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals("测试供应商A", document.getSupplierName());
        assertEquals("PO-TEST-20260909-001", document.getDocumentNo());
        assertEquals("TEST-PIPE-001", document.getItems().getFirst().getProductCode());
        assertEquals("测试防腐保温管", document.getItems().getFirst().getProductName());
        assertEquals("Excel导入测试-第一条明细", document.getItems().getFirst().getRemark());
        assertNull(document.getRemark());
    }

    @Test
    void shouldExtractOnlyStandaloneDocumentRemark() {
        String text = "供应商：测试供应商A\n订单日期：2026-09-09\n结算账户名称：基本账户\n"
                + "备注：仅保留这一条\n"
                + "产品编码 产品名称 数量 单价 税率(%) 备注\n"
                + "TEST-PIPE-001 测试防腐保温管 10 128.50 13 Excel明细备注";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals("仅保留这一条", document.getRemark());
        assertEquals("基本账户", document.getAccountName());
        assertEquals("Excel明细备注", document.getItems().getFirst().getRemark());
        assertFalse(document.getRemark().contains("TEST-PIPE-001"));
    }

    @Test
    void shouldBoundPrefixedLabeledFields() {
        String text = "供应商名称：测试供应商A 联系人姓名：张三 手机号码：13800138000 "
                + "开户银行：中国银行 开户账号：6222000000000000";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals("测试供应商A", document.getSupplierName());
        assertEquals("张三", document.getContact());
        assertEquals("13800138000", document.getMobile());
        assertEquals("中国银行", document.getBankName());
        assertEquals("6222000000000000", document.getBankAccount());
    }

    @Test
    void shouldNormalizeTaxPercentFromAsciiFullWidthAndExcelValues() {
        String text = "供应商：测试供应商A\n订单日期：2026-09-09\n"
                + "A-001 测试管 10 100 13% 第一条\n"
                + "A-002 测试板 2 50 13％ 第二条\n"
                + "A-003 测试件 1 20 0.13 第三条";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals(3, document.getItems().size());
        assertEquals("13", document.getItems().get(0).getTaxPercent().toPlainString());
        assertEquals("13", document.getItems().get(1).getTaxPercent().toPlainString());
        assertEquals("13", document.getItems().get(2).getTaxPercent().toPlainString());
    }

    @Test
    void shouldExtractTaxPercentFromLabeledOcrTextAndKeepOnlyRemark() {
        String text = "供应商：测试供应商A\n订单日期：2026-09-09\n"
                + "A-001 测试管 10 100 税率：13% 第一条备注\n"
                + "A-002 测试板 2 50 税率(%) 13 第二条备注\n"
                + "A-003 测试件 1 20 13% 第三条备注";

        ErpRecognitionDocument document = buildDocument(text);

        assertEquals(3, document.getItems().size());
        assertEquals("13", document.getItems().get(0).getTaxPercent().toPlainString());
        assertEquals("第一条备注", document.getItems().get(0).getRemark());
        assertEquals("13", document.getItems().get(1).getTaxPercent().toPlainString());
        assertEquals("第二条备注", document.getItems().get(1).getRemark());
        assertEquals("13", document.getItems().get(2).getTaxPercent().toPlainString());
        assertEquals("第三条备注", document.getItems().get(2).getRemark());
    }

    private ErpRecognitionDocument buildDocument(String text) {
        TesseractDocumentRecognitionProvider provider = new TesseractDocumentRecognitionProvider();
        String response = ReflectionTestUtils.invokeMethod(provider, "buildResponse", text, "PURCHASE_ORDER");
        Map<String, Object> result = JsonUtils.parseMap(response);
        return JsonUtils.parseObject(JsonUtils.toJsonString(result), ErpRecognitionDocument.class);
    }
}
