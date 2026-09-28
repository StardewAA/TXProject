package cn.iocoder.txgy.module.bidding.infra.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 公文格式（GB/T 9704-2012）招标公告 PDF 生成器。
 *
 * <p>版式标准（《党政机关公文格式》GB/T 9704-2012 及招标公告实务）：
 * <ul>
 *   <li>A4 版心：上 37mm / 下 35mm / 左 28mm / 右 26mm（= 上104.9 / 下99.2 / 左79.4 / 右73.7 pt）；</li>
 *   <li>版头：红色发文机关标志（黑体 22pt）+ 红色反线分隔；</li>
 *   <li>标题：二号小标宋居中（本机无小标宋 → 黑体 22pt 替代，正式环境可换方正小标宋）；</li>
 *   <li>正文：三号仿宋_GB2312（simfang.ttf，16pt），行距固定 28pt，首行缩进 2 字符；</li>
 *   <li>一级标题（一、…）：三号黑体；二级标题（（一）…）：三号楷体；</li>
 *   <li>落款：署名右空二字、成文日期右空四字；红色公章盖于成文日期（骑年盖月）；</li>
 *   <li>页码：页脚版心下缘之下、右空一字「— 1 —」四号宋体。</li>
 * </ul>
 *
 * <p>定位：元数据（标题/文号/正文段落/落款/日期/印章单位）→ 公文版式 PDF 渲染；
 * 产出 PDF 后再走 CA 签章（SM2 真实签名 + 电子印章）形成法律效力闭环（《电子签名法》13/14 条）。
 */
public final class GongWenNoticePdfUtils {

    private GongWenNoticePdfUtils() {
    }

    /** 招标公告结构化元数据（对应 NoticeDO.fieldsJson 的渲染子集）。 */
    public static class NoticeMeta {
        /** 版头发文机关标志（红色），如「××市公共资源交易中心」；可为空表示无红头 */
        public String organ;
        /** 发文字号/招标编号（版头下方），可为空 */
        public String noticeNo;
        /** 标题（二号居中） */
        public String title;
        /** 正文段落（一、/（一）/第一章 等行自动识别标题字体） */
        public List<String> body;
        /** 落款署名行（右空二字），如「招标人：××建设发展有限公司」 */
        public List<String> signatories;
        /** 成文日期（右空四字），如「2026年9月28日」 */
        public String dateLine;
        /** 印章单位名（公章内单位名） */
        public String sealOrg;
    }

    // 版心参数（pt）：上37mm/下35mm/左28mm/右26mm；A4 = 595x842
    private static final float MARGIN_LEFT = 79.4f;
    private static final float MARGIN_TOP = 104.9f;
    private static final float MARGIN_BOTTOM = 99.2f;
    private static final float MARGIN_RIGHT = 73.7f;
    private static final float USABLE_W = 595.0f - MARGIN_LEFT - MARGIN_RIGHT; // 441.9
    private static final float USABLE_H = 842.0f - MARGIN_TOP - MARGIN_BOTTOM; // 637.9
    private static final float LINE_H = 28f;                                  // 行距 28pt
    private static final float BODY_SIZE = 16f;                               // 三号
    private static final float TITLE_SIZE = 22f;                              // 二号
    private static final float RED = 200f / 255f, GREEN = 16f / 255f, BLUE = 16f / 255f; // 印章/红头红色（PDFBox 需 0..1）

    /** 生成公文版式 PDF（含红色公章），返回字节。 */
    public static byte[] generate(NoticeMeta meta) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont fs = loadFont(doc, "C:/Windows/Fonts/simfang.ttf"); // 仿宋
            PDFont ht = loadFont(doc, "C:/Windows/Fonts/simhei.ttf");  // 黑体
            PDFont kt = loadFont(doc, "C:/Windows/Fonts/simkai.ttf");  // 楷体
            // 页码字体复用 fs（同一字体文件只 load 一次，避免重复内嵌字体程序导致 COSWriter 报错）
            PageWriter w = new PageWriter(doc, fs, ht, kt, fs);

            // ===== 版头（红头 + 红反线） =====
            if (meta.organ != null && !meta.organ.isEmpty()) {
                w.red();
                w.font(ht, TITLE_SIZE);
                String line = w.centerLine(meta.organ, TITLE_SIZE);
                w.text(line, TITLE_SIZE, true);
                // 红色反线（横贯版心）
                w.separator();
            }
            w.black();

            // ===== 标题 =====
            w.font(ht, TITLE_SIZE);
            for (String tl : w.wrap(meta.title, TITLE_SIZE)) {
                w.text(w.centerLine(tl, TITLE_SIZE), TITLE_SIZE, true);
            }
            w.skip(10f);

            // ===== 发文字号（可选，三号仿宋居中） =====
            if (meta.noticeNo != null && !meta.noticeNo.isEmpty()) {
                w.font(fs, BODY_SIZE);
                w.text(w.centerLine(meta.noticeNo, BODY_SIZE), BODY_SIZE, true);
                w.skip(14f);
            }

            // ===== 正文 =====
            for (String para : meta.body) {
                if (para == null || para.isEmpty()) {
                    w.skip(12f);
                    continue;
                }
                PDFont pf = fs;
                float indent = BODY_SIZE * 2; // 首行缩进 2 字
                if (para.matches("^[一二三四五六七八九十]+、.*")) {
                    pf = ht;      // 一级标题：黑体
                    indent = 0f;
                } else if (para.startsWith("（") && para.contains("）")) {
                    pf = kt;      // 二级标题：楷体
                    indent = BODY_SIZE * 2;
                } else if (para.startsWith("第") && para.contains("章")) {
                    pf = ht;
                    indent = 0f;
                }
                w.font(pf, BODY_SIZE);
                boolean first = true;
                for (String l : w.wrap(para, BODY_SIZE)) {
                    w.para(l, BODY_SIZE, first ? indent : 0f);
                    first = false;
                }
                w.skip(6f);
            }

            // ===== 落款 =====
            w.skip(20f);
            w.font(fs, BODY_SIZE);
            for (String sig : meta.signatories) {
                w.rightText(sig, BODY_SIZE, BODY_SIZE * 2);
            }
            float dateBaseline = 0f;
            if (meta.dateLine != null) {
                dateBaseline = w.rightText(meta.dateLine, BODY_SIZE, BODY_SIZE * 4);
            }

            // ===== 红色公章：盖于成文日期（骑年盖月），右对齐压日期 =====
            if (meta.sealOrg != null && !meta.sealOrg.isEmpty() && dateBaseline > 0) {
                w.seal(meta.sealOrg, meta.dateLine, dateBaseline);
            }

            // ===== 页码：页脚「— N —」四号宋体，版心下缘之下右空一字（用当前真实页号） =====
            w.pageNumber(w.pageNo);
            w.finish(); // 必须先关闭内容流再保存

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    /** 流式页面书写器：坐标自增、越界自动换页、首行缩进/居中/右对齐、印章。 */
    private static final class PageWriter {
        private final PDDocument doc;
        private final PDFont fs, ht, kt, st;
        private PDPageContentStream cs;
        private PDPage page;
        private float y; // 当前书写基线（自上而下）
        private int pageNo = 1;
        private PDFont currentFont; // 当前字体（width() 计算用）

        PageWriter(PDDocument doc, PDFont fs, PDFont ht, PDFont kt, PDFont st) throws IOException {
            this.doc = doc;
            this.fs = fs;
            this.ht = ht;
            this.kt = kt;
            this.st = st;
            newPage();
        }

        /** 结束当前页书写（必须在 doc.save() 前调用，否则流未关闭会报 open stream writer）。 */
        void finish() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = 842.0f - MARGIN_TOP;
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM + 20f) {
                pageNumber(pageNo);
                newPage();
                pageNo++;
            }
        }

        void font(PDFont f, float size) throws IOException {
            cs.setFont(f, size);
            currentFont = f;
        }

        void black() throws IOException {
            cs.setNonStrokingColor(0, 0, 0);
            cs.setStrokingColor(0, 0, 0);
        }

        void red() throws IOException {
            cs.setNonStrokingColor(RED, GREEN, BLUE);
            cs.setStrokingColor(RED, GREEN, BLUE);
        }

        /** 文本宽度（pt）。 */
        private float width(String s, float size) throws IOException {
            return currentFont.getStringWidth(s) / 1000f * size;
        }

        /** 写一行（基线为 y - size），并下移 size+lineH。 */
        void text(String s, float size, boolean center) throws IOException {
            ensure(size + LINE_H);
            cs.setFont(currentFont, size);
            float x = MARGIN_LEFT;
            if (center) {
                x += (USABLE_W - width(s, size)) / 2f;
            }
            cs.beginText();
            cs.newLineAtOffset(x, y - size);
            cs.showText(s);
            cs.endText();
            y -= size + LINE_H;
        }

        /** 正文段一行（带首行缩进）。 */
        void para(String s, float size, float indent) throws IOException {
            ensure(size + LINE_H);
            cs.setFont(currentFont, size);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT + indent, y - size);
            cs.showText(s);
            cs.endText();
            y -= size + LINE_H;
        }

        /** 右对齐（右空 rightGapPt 字位），返回该行基线 Y（用于印章定位）。 */
        float rightText(String s, float size, float rightGapPt) throws IOException {
            ensure(size + LINE_H);
            cs.setFont(currentFont, size);
            float x = MARGIN_LEFT + USABLE_W - width(s, size) - rightGapPt;
            cs.beginText();
            cs.newLineAtOffset(x, y - size);
            cs.showText(s);
            cs.endText();
            float baseline = y - size;
            y -= size + LINE_H;
            return baseline;
        }

        /** 版头红色反线（横贯版心，宽约 3pt 的红色横线）。 */
        void separator() throws IOException {
            float sy = y - 12f;
            cs.setStrokingColor(RED, GREEN, BLUE);
            cs.setLineWidth(2.4f);
            cs.moveTo(MARGIN_LEFT, sy);
            cs.lineTo(MARGIN_LEFT + USABLE_W, sy);
            cs.stroke();
            cs.setLineWidth(1f);
            black();
            y = sy - 14f;
        }

        /** 空行下移。 */
        void skip(float dy) throws IOException {
            ensure(dy);
            y -= dy;
        }

        /** 折行：按字号近似每行最大字符数（中文字宽≈字号）。 */
        List<String> wrap(String text, float size) {
            int maxChars = (int) (USABLE_W / size);
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
                cur.append(c);
                if (cur.length() >= maxChars && i + 1 < text.length()) {
                    // 尽量避免行首为标点（简单策略：行末标点允许，行首标点回退）
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) {
                lines.add(cur.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        /** 居中行文本（计算前导空格使视觉居中）。 */
        String centerLine(String s, float size) {
            return s;
        }

        /** 页脚页码（四号宋体，版心下缘之下、右空一字）。 */
        void pageNumber(int no) throws IOException {
            cs.setFont(st, 14f);
            String pg = "— " + no + " —";
            float tw = st.getStringWidth(pg) / 1000f * 14f;
            float x = MARGIN_LEFT + USABLE_W - tw - 14f;
            float py = MARGIN_BOTTOM - 14f - 10f;
            cs.beginText();
            cs.newLineAtOffset(x, py);
            cs.showText(pg);
            cs.endText();
        }

        /** 红色公章：中心 (cx, cy)，盖于成文日期之上。 */
        void seal(String orgName, String dateLine, float baseline) throws IOException {
            float r = 36f;
            float cx = MARGIN_LEFT + USABLE_W - 64f - stWidth(dateLine, BODY_SIZE) + r - 8f;
            float cy = baseline + 12f;
            if (cx - r < MARGIN_LEFT) {
                cx = MARGIN_LEFT + r;
            }
            if (cy + r > 842.0f - MARGIN_TOP) {
                cy = 842.0f - MARGIN_TOP - r;
            }
            red();
            cs.setLineWidth(2.0f);
            addCircle(cs, cx, cy, r);
            cs.stroke();
            cs.setLineWidth(0.8f);
            addCircle(cs, cx, cy, r - 4.5f);
            cs.stroke();
            // 五角星
            float starR = 12f;
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(-90 + i * 36);
                float rr = (i % 2 == 0) ? starR : starR * 0.382f;
                float x = cx + rr * (float) Math.cos(a);
                float yp = cy + rr * (float) Math.sin(a);
                if (i == 0) {
                    cs.moveTo(x, yp);
                } else {
                    cs.lineTo(x, yp);
                }
            }
            cs.closePath();
            cs.fill();
            // 单位名（星上方）
            cs.setFont(fs, 7f);
            float tw = fs.getStringWidth(orgName) / 1000f * 7f;
            cs.beginText();
            cs.newLineAtOffset(cx - tw / 2f, cy + starR * 1.1f);
            cs.showText(orgName);
            cs.endText();
            // 日期（星下方）
            cs.setFont(fs, 5.5f);
            float dw = fs.getStringWidth(dateLine) / 1000f * 5.5f;
            cs.beginText();
            cs.newLineAtOffset(cx - dw / 2f, cy - starR * 1.1f - 5.5f);
            cs.showText(dateLine);
            cs.endText();
            black();
        }

        private float stWidth(String s, float size) throws IOException {
            return fs.getStringWidth(s) / 1000f * size;
        }
    }

    private static void addCircle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = 0.5522847498f * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    private static PDFont loadFont(PDDocument doc, String path) throws IOException {
        File f = new File(path);
        if (!f.isFile()) {
            throw new IOException("缺少字体：" + path);
        }
        // 与 LocalSealUtils 同款加载模式（实测可保存）：try-with-resources 内 load，每个字体文件仅加载一次
        try (FileInputStream in = new FileInputStream(f)) {
            return PDType0Font.load(doc, in);
        }
    }
}
