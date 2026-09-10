package cn.iocoder.txgy.tool.pdftoimage.demo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;

/**
 * 自测用样例 PDF 生成器（模拟两页资质证明：标题+边框+彩色印章区）。
 * 用法: java -cp ... demo.SamplePdfGenerator <输出.pdf>
 */
public final class SamplePdfGenerator {

    private SamplePdfGenerator() {
    }

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "sample-cert.pdf";
        try (PDDocument doc = new PDDocument()) {
            // 第 1 页：证书主面
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.setNonStrokingColor(0.55f, 0.22f, 0.19f); // 棕红边框（模拟资质证书）
                cs.setLineWidth(2f);
                cs.addRect(40, 40, page1.getMediaBox().getWidth() - 80, page1.getMediaBox().getHeight() - 80);
                cs.stroke();
                cs.setLineWidth(0.5f);
                cs.addRect(48, 48, page1.getMediaBox().getWidth() - 96, page1.getMediaBox().getHeight() - 96);
                cs.stroke();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 24);
                cs.newLineAtOffset(140, 720);
                cs.showText("QUALIFICATION CERTIFICATE");
                cs.setFont(PDType1Font.HELVETICA, 14);
                cs.newLineAtOffset(0, -60);
                cs.showText("Enterprise: DEMO CONSTRUCTION CO., LTD.");
                cs.newLineAtOffset(0, -30);
                cs.showText("Level: Grade 1 - General Contracting");
                cs.newLineAtOffset(0, -30);
                cs.showText("Scope: Municipal Public Works");
                cs.newLineAtOffset(0, -30);
                cs.showText("Cert No.: A132000058");
                cs.endText();

                // 模拟电子印章区（红色方块）
                cs.setNonStrokingColor(0.85f, 0.15f, 0.15f);
                cs.addRect(400, 130, 120, 120);
                cs.fill();
            }

            // 第 2 页：变更/延续记录页
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
                cs.newLineAtOffset(80, 740);
                cs.showText("CHANGE / RENEWAL RECORD (PAGE 2)");
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(0, -40);
                cs.showText("2026-01-15  Renewal, valid to 2031-01-14");
                cs.newLineAtOffset(0, -24);
                cs.showText("2025-06-01  Legal representative changed");
                cs.endText();
            }
            doc.save(path);
        }
        System.out.println("样例PDF已生成: " + new File(path).getAbsolutePath());
    }

}
