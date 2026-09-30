package cn.iocoder.txgy.module.bidding.infra.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** 渲染 PDF 首页为 PNG（供演示查看）。 */
public class PdfToPngDemo {
    public static void main(String[] args) throws Exception {
        String in = args[0];
        String outPng = args.length > 1 ? args[1] : in.replace(".pdf", ".png");
        try (PDDocument doc = Loader.loadPDF(new File(in))) {
            PDFRenderer r = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                BufferedImage img = r.renderImageWithDPI(i, 110);
                String name = pages == 1 ? outPng : outPng.replace(".png", "-" + (i + 1) + ".png");
                ImageIO.write(img, "png", new File(name));
                System.out.println("page " + (i + 1) + " -> " + name + " (" + img.getWidth() + "x" + img.getHeight() + ")");
            }
        }
    }
}