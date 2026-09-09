package cn.iocoder.txgy.tool.pdftoimage.core;

import cn.iocoder.txgy.tool.pdftoimage.api.PageImage;
import cn.iocoder.txgy.tool.pdftoimage.api.PdfToImageOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 基于 Apache PDFBox 2.0.x 的实现（Apache-2.0，可内嵌复用）。
 *
 * <p>已知事项：PDF 未内嵌中文字体（如 STSong-Light）时可能出现缺字，需用真实证书样本回归；
 * 必要时注册字体映射（FontMapperImpl 指向本机 ttf）。</p>
 */
public class PdfBoxPdfToImageRenderer implements PdfToImageRenderer {

    @Override
    public List<PageImage> convert(File pdf, File outDir, PdfToImageOptions options) throws IOException {
        if (pdf == null || !pdf.isFile()) {
            throw new IOException("PDF 不存在或不是普通文件: " + (pdf == null ? "null" : pdf.getAbsolutePath()));
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outDir.getAbsolutePath());
        }
        ImageType imageType = options.isWhiteBackground() ? ImageType.RGB : ImageType.ARGB;
        PDFRenderer renderer;
        try (PDDocument document = PDDocument.load(pdf)) {
            renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new IOException("PDF 无页面: " + pdf.getName());
            }
            String base = stripExt(pdf.getName());
            List<PageImage> results = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                BufferedImage raw = renderer.renderImageWithDPI(i, options.getDpi(), imageType);
                BufferedImage capped = capResolution(raw, options);
                File out = new File(outDir, base + "_p" + (i + 1) + "." + options.getFormatExt());
                if (!options.isOverwrite() && out.exists()) {
                    // 幂等：已存在且未要求覆盖时跳过写盘（幂等约定在 Batch 层统一输出，单文件 API 也保持一致）
                    results.add(new PageImage(i + 1, capped.getWidth(), capped.getHeight(), out));
                    continue;
                }
                writeImage(capped, out, options);
                results.add(new PageImage(i + 1, capped.getWidth(), capped.getHeight(), out));
            }
            return results;
        }
    }

    /** 按 maxWidth/maxHeight 等比缩小（只缩不放） */
    private BufferedImage capResolution(BufferedImage raw, PdfToImageOptions options) {
        int w = raw.getWidth();
        int h = raw.getHeight();
        int maxW = options.getMaxWidth();
        int maxH = options.getMaxHeight();
        if (maxW > 0 && maxH > 0 && (w > maxW || h > maxH)) {
            double scale = Math.min((double) maxW / w, (double) maxH / h);
            if (scale < 1.0) {
                int nw = Math.max(1, (int) Math.round(w * scale));
                int nh = Math.max(1, (int) Math.round(h * scale));
                BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = dst.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(raw, 0, 0, nw, nh, null);
                } finally {
                    g.dispose();
                }
                return dst;
            }
        }
        return raw;
    }

    /** 输出图片（含 DPI 元数据写入，尽力而为；JPG 白底合成并应用质量参数） */
    private void writeImage(BufferedImage src, File out, PdfToImageOptions options) throws IOException {
        String formatId = options.getFormat().toLowerCase(Locale.ROOT); // png / jpg
        BufferedImage image = src;
        if ("jpg".equals(formatId)) {
            // JPEG 无透明：白底合成
            if (src.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.drawImage(src, 0, 0, null);
                } finally {
                    g.dispose();
                }
                image = rgb;
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatId);
        if (!writers.hasNext()) {
            throw new IOException("无可用 ImageWriter: " + formatId);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpg".equals(formatId) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality((float) options.getJpegQuality());
            }
            IIOMetadata meta = attachDpiMetadata(writer, image, param, formatId, options.getDpi());
            writer.write(null, new IIOImage(image, null, meta), param);
        } finally {
            writer.dispose();
        }
    }

    /** 尽量写入 DPI 元数据（PNG pHYs / JPEG JFIF density），失败静默（不影响成图） */
    private IIOMetadata attachDpiMetadata(ImageWriter writer, BufferedImage image,
                                          ImageWriteParam param, String formatId, int dpi) {
        try {
            IIOMetadata meta = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), param);
            if ("png".equals(formatId)) {
                IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
                IIOMetadataNode phys = new IIOMetadataNode("pHYs");
                long perMeter = Math.round(dpi * 100.0 / 2.54);
                phys.setAttribute("pixelsPerUnitXAxis", String.valueOf(perMeter));
                phys.setAttribute("pixelsPerUnitYAxis", String.valueOf(perMeter));
                phys.setAttribute("unitSpecifier", "1"); // 1 = 米
                root.appendChild(phys);
                if (meta.isStandardMetadataFormatSupported()) {
                    meta.mergeTree("javax_imageio_png_1.0", root);
                }
            } else if ("jpg".equals(formatId)) {
                IIOMetadataNode tree = new IIOMetadataNode("javax_imageio_jpeg_1.0");
                IIOMetadataNode variety = new IIOMetadataNode("JPEGvariety");
                IIOMetadataNode jfif = new IIOMetadataNode("app0JFIF");
                jfif.setAttribute("majorVersion", "1");
                jfif.setAttribute("minorVersion", "2");
                jfif.setAttribute("resUnits", "1"); // 1 = dots per inch
                jfif.setAttribute("Xdensity", String.valueOf(dpi));
                jfif.setAttribute("Ydensity", String.valueOf(dpi));
                jfif.setAttribute("thumbWidth", "0");
                jfif.setAttribute("thumbHeight", "0");
                variety.appendChild(jfif);
                tree.appendChild(variety);
                meta.mergeTree("javax_imageio_jpeg_1.0", tree);
            }
            return meta;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripExt(String name) {
        int idx = name.toLowerCase(Locale.ROOT).lastIndexOf(".pdf");
        return idx > 0 ? name.substring(0, idx) : name;
    }

}
