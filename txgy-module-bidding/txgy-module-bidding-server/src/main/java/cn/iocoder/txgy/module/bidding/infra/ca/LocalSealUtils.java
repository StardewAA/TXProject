package cn.iocoder.txgy.module.bidding.infra.ca;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 本地电子签章文件生成（测试配置 bidding.ca.sdk.enabled=false 时使用）。
 *
 * <p>定位：用户裁定「即使是模拟的 CA 盖章、未获官方认可，也要最终能在文件上进行签名」——
 * 本工具对文件内容做**真实 SM2 签名**（见 {@link LocalCryptoUtils}），并把签章结果落到文件上：
 * <ul>
 *   <li>PDF 文件：用 PDFBox 在首页绘制红色公章（双圆 + 五角星 + 单位名 + 证书序列号 + 签名时间），
 *       输出「已签章」PDF（文件本体带可见签章）；</li>
 *   <li>其他文件：生成同名 {@code .sig.txt} 分离式签名信息文件（SM3 指纹 + SM2 签名值 + 证书号 +
 *       单位 + 时间），供验签/归档。</li>
 * </ul>
 *
 * <p>签名是密码学真实产生的（统一测试账号密钥对，可自洽验签），仅证书未获官方 CA 认可；
 * 正式环境由 CA 共享组件/H5 签章组件完成（30 号文档后端待办 #2/#5）。
 */
public final class LocalSealUtils {

    private LocalSealUtils() {
    }

    /** 常见中文字体路径（Windows simhei / Linux wqy-zenhei，供 PDF 中文显示；缺省回退英文） */
    private static final String[] CJK_FONT_CANDIDATES = {
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
    };

    /**
     * 判断是否为 PDF（魔数 %PDF-）。
     */
    public static boolean isPdf(byte[] content) {
        if (content == null || content.length < 5) {
            return false;
        }
        return content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F' && content[4] == '-';
    }

    /**
     * 对 PDF 首页绘制红色电子公章（双圆 + 五角星 + 单位名 + 证书号 + 时间），返回已签章 PDF 字节。
     *
     * @param pdfBytes  原始 PDF
     * @param orgName   签章单位名（中文优先；中文字体不可用时回退英文提示）
     * @param certSn    CA 证书序列号
     * @param signDate  签名时间（yyyy-MM-dd HH:mm:ss）
     */
    public static byte[] sealPdf(byte[] pdfBytes, String orgName, String certSn, String signDate) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                return pdfBytes;
            }
            PDFont cjk = loadCjkFont(doc);
            PDFont latin = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            // 每页盖章：招标文件等多为数十页，逐页盖红章更贴近真实签章（骑缝/每页章口径）
            for (int i = 0; i < pages; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle media = page.getMediaBox();
                float cx = media.getWidth() - 105f;
                float cy = media.getHeight() - 105f;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawRedSeal(cs, cjk, latin, cx, cy, orgName, certSn, signDate);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 签章绘制失败", e);
        }
    }

    /** 在指定页面绘制红色电子公章（双圆 + 五角星 + 单位名 + 证书号 + 时间）。 */
    private static void drawRedSeal(PDPageContentStream cs, PDFont cjk, PDFont latin,
                                    float cx, float cy, String orgName, String certSn, String signDate) throws IOException {
        float r = 62f;
        // 红章双圆
        // PDFBox 3.x：setStrokingColor(float,float,float) 语义为 0..1 浮点；0-255 颜色用 Color 重载
        cs.setStrokingColor(new Color(200, 16, 16));
        cs.setNonStrokingColor(new Color(200, 16, 16));
        cs.setLineWidth(2.6f);
        addCircle(cs, cx, cy, r);
        cs.stroke();
        cs.setLineWidth(1.1f);
        addCircle(cs, cx, cy, r - 9f);
        cs.stroke();
        // 五角星
        float starR = 24f;
        float[] xs = new float[10];
        float[] ys = new float[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float rr = (i % 2 == 0) ? starR : starR * 0.382f;
            xs[i] = cx + rr * (float) Math.cos(angle);
            ys[i] = cy + rr * (float) Math.sin(angle);
        }
        cs.moveTo(xs[0], ys[0]);
        for (int i = 1; i < 10; i++) {
            cs.lineTo(xs[i], ys[i]);
        }
        cs.closePath();
        cs.fill();
        // 单位名（五角星上方）
        cs.setFont(cjk, 10f);
        cs.beginText();
        cs.newLineAtOffset(cx - 40f, cy + starR + 4f);
        cs.showText(shorten(orgName, 14));
        cs.endText();
        // 证书序列号（五角星下方两行）
        cs.setFont(cjk, 7f);
        cs.beginText();
        cs.newLineAtOffset(cx - 48f, cy - starR - 8f);
        cs.showText("SN: " + shorten(certSn, 26));
        cs.endText();
        cs.beginText();
        cs.newLineAtOffset(cx - 48f, cy - starR - 17f);
        cs.showText(signDate);
        cs.endText();
        // 底部签名标注
        cs.setFont(latin, 6f);
        cs.beginText();
        cs.newLineAtOffset(cx - 30f, cy - r - 14f);
        cs.showText("SM2 Signature (Local Test CA)");
        cs.endText();
    }

    /**
     * 生成分离式签名信息文件内容（非 PDF 文件用，UTF-8）。
     *
     * @param fileName   原文件名
     * @param sm3Hex     SM3 指纹
     * @param sha256Hex  SHA-256
     * @param signature  SM2 签名值（base64）
     * @param certSn     证书序列号
     * @param orgName    单位名
     * @param signDate   签名时间
     */
    public static String buildSignatureInfo(String fileName, String sm3Hex, String sha256Hex,
                                            String signature, String certSn, String orgName, String signDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== CA 电子签章信息（本地统一测试账号，未获官方 CA 认可）==========\n");
        sb.append("文件名: ").append(fileName).append("\n");
        sb.append("文件指纹(SM3): ").append(sm3Hex).append("\n");
        sb.append("文件SHA-256: ").append(sha256Hex).append("\n");
        sb.append("证书序列号: ").append(certSn).append("\n");
        sb.append("签名单位: ").append(orgName).append("\n");
        sb.append("签名算法: SM2（GB/T 32918）\n");
        sb.append("签名时间: ").append(signDate).append("\n");
        sb.append("签名值(SM2 base64): ").append(signature).append("\n");
        sb.append("验签方式: 使用统一测试账号公钥验签（LocalCryptoUtils.sm2VerifyBase64）\n");
        return sb.toString();
    }

    /** 用 4 段三次贝塞尔曲线近似画圆（PDFBox 2.x 无 addEllipse，k=0.5523 为圆弧近似常数）。 */
    private static void addCircle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = 0.5522847498f * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    private static PDFont loadCjkFont(PDDocument doc) {
        for (String path : CJK_FONT_CANDIDATES) {
            File f = new File(path);
            if (f.isFile()) {
                try {
                    return PDType0Font.load(doc, new FileInputStream(f));
                } catch (IOException ignore) {
                    // 尝试下一个候选字体
                }
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 便捷：字节转 hex（SHA-256 等通用） */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
