package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderRespVO;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStream;
import java.util.Collections;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErpPurchaseOrderPdfExporterTest {

    @Test
    void shouldWritePdfResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErpPurchaseOrderPdfExporter.write(response,
                Collections.singletonList(new ErpPurchaseOrderRespVO()));

        byte[] content = response.getContentAsByteArray();
        assertTrue(content.length > 100);
        assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'},
                new byte[]{content[0], content[1], content[2], content[3]});
        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition")
                .contains(URLEncoder.encode("采购订单.pdf", StandardCharsets.UTF_8)));

        try (PdfReader reader = new PdfReader(content)) {
            PdfDictionary resources = reader.getPageN(1).getAsDict(PdfName.RESOURCES);
            PdfDictionary xObjects = resources == null ? null : resources.getAsDict(PdfName.XOBJECT);
            assertTrue(xObjects != null && xObjects.getKeys().size() >= 2,
                    "PDF 首页应同时包含页眉和页脚图片资源");

            try (InputStream footerStream = ErpPurchaseOrderPdfExporter.class
                    .getResourceAsStream("/pdf/tianxin-pipe-industry-footer-cropped.png")) {
                assertNotNull(footerStream, "裁剪后的 PDF 页脚资源必须被打包");
                Image footer = Image.getInstance(footerStream.readAllBytes());
                assertEquals(1377, footer.getWidth());
                assertEquals(76, footer.getHeight());
            }
        }
    }
}
