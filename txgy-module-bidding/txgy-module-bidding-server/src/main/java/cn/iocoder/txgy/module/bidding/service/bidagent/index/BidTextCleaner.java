package cn.iocoder.txgy.module.bidding.service.bidagent.index;

// [规范依据]《招投标模块开发规范》§5（检索链路：入库文本必须可复算地清洗）
// [业务目标] 索引阶段把 Tika 抽取的"原始全文"清洗为"可切分文本"：去噪 → 结构归一 → 编码统一
// [为什么] 解析结果里混着页码、页眉页脚、水印、硬换行、全角半角混排、零宽字符与私用区符号；
//          直接切片会让同一份资料在不同来源/不同编码下产出不同片段，召回不可复算。
//          清洗集中在入库一处完成；检索侧只做同口径的"检索词归一"，避免两边各洗一遍反而对不上。

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 入库文本清洗器（纯 JDK 实现，无 Spring 依赖，便于单测与复算）。
 *
 * <p>三步处理：
 * <ol>
 *   <li><b>编码统一</b>：换行统一为 {@code \n}；去 BOM/零宽/软连字符/私用区符号；控制字符只保留换行；
 *       各类不可见空白（NBSP、全角空格、窄空格）折为半角空格；逐码点做兼容归一（NFKC），
 *       把全角字母数字、全角运算符、罗马数字、带圈序号折成半角——但保留中文标点
 *       （{@code ，。；：！？（）《》…} 等）不改写，避免把中文排版改成英文标点。</li>
 *   <li><b>去噪</b>：删页码（含裸数字页码序列）、重复出现的页眉页脚、水印行、纯符号装饰行、
 *       相邻重复段落；解析残留的替换符与私用区字符一并清掉。</li>
 *   <li><b>结构归一</b>：分页符 {@code \f} 视为段落边界；合并 PDF 抽取造成的硬换行（行被折断），
 *       但遇到句末标点或新条款/新标题起始标记时不合并；连续空白压成单空格；目录点线折为省略号；
 *       段落一律以单个 {@code \n} 分隔（不留空行）。</li>
 * </ol>
 *
 * <p>注意：本清洗只改文本表示，不删证据内容——被删的只有页码/页眉页脚/水印/装饰与重复段，
 * 且每次清洗都会返回统计（{@link Result}），写进入库日志便于复核。
 */
public final class BidTextCleaner {

    /** 硬换行合并的最小上一行长度：短于该长度视作表头/表格单元，不合并（保护表格结构） */
    private static final int WRAP_MERGE_MIN_LINE_LEN = 20;
    /** 合并后的段落长度上限：到顶即断开，避免表格数据行被接成超长段落 */
    private static final int WRAP_MERGE_MAX_LINE_LEN = 160;
    /** 页眉/页脚候选行的最大长度 */
    private static final int RUNNING_LINE_MAX_LEN = 30;
    /** 页眉/页脚判定：最少重复次数与最小占比（两条同时满足才删） */
    private static final int RUNNING_LINE_MIN_COUNT = 3;
    private static final double RUNNING_LINE_MIN_RATIO = 0.08d;
    /** 裸数字页码序列判定：最少个数、相邻步长上限、非递减对占比 */
    private static final int PAGE_SEQ_MIN_SIZE = 3;
    private static final int PAGE_SEQ_MAX_STEP = 5;
    private static final double PAGE_SEQ_MIN_ASCENT_RATIO = 0.7d;
    /** 表格单元的单词长度上限：超过该长度说明是整句，不是逐格抽取的表头 */
    private static final int TABLE_TOKEN_MAX_LEN = 12;
    /** 装饰性重复符号（含破折号）：连续 3 个以上压成 1 个；成对破折号"——"保留 */
    private static final Pattern DECORATION_RUN = Pattern.compile("([=_~*+#>\\-—])\\1{2,}");
    /** 目录点线（4 个以上）折为省略号 */
    private static final Pattern DOT_LEADER = Pattern.compile("[.．·]{4,}");
    /** 行内连续空白（含制表符）压成单空格 */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t\\x0B\\x0C]{2,}");
    /** 零宽字符、BOM、软连字符、双向控制符（不可见但会污染切片与向量） */
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\uFEFF\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u00AD]");
    /** 控制字符：只保留换行 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    /** 各种不可见空白 → 半角空格 */
    private static final Pattern INVISIBLE_SPACE = Pattern.compile(
            "[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\u2007]");
    /** 私用区字符（Wingdings 等项目符号，Tika 抽取后是无意义乱码） */
    private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]");
    /** 解析失败占位符 */
    private static final Pattern REPLACEMENT_CHAR = Pattern.compile("[\\uFFFD]");
    /** 纯符号/装饰行（无字母、无数字、无汉字）：删 */
    private static final Pattern PURE_DECORATION = Pattern.compile("^[\\p{P}\\p{S}]+$");
    /** 需要保护的填空占位行：即使重复出现也不当页眉页脚删（属于模板正文结构） */
    private static final Pattern PLACEHOLDER_LINE = Pattern.compile(
            "(年\\s*月\\s*日|公章|签字|签名|盖章|身份证|（\\s*）|\\(\\s*\\))");
    /** 结构起始标记：新条款/新标题，不与上一行合并 */
    private static final Pattern STRUCTURE_START = Pattern.compile(
            "^("
                    + "第\\s*[0-9一二三四五六七八九十百千]+\\s*[章节条款项编部分]"
                    + "|附\\s*[表件录]"
                    + "|[（(【\\[]\\s*[0-9一二三四五六七八九十]+\\s*[)）\\]】]"
                    + "|[0-9]+(\\.[0-9]+)*\\s*[、.．)）]"
                    + "|[一二三四五六七八九十]+\\s*[、.]"
                    + "|[-*•·▪●○◆■□▲△]\\s"
                    + ")");
    /** 字段标签行（"乙方：投标方""姓名： 性别：" 这类短前缀+冒号）：属新字段，不与上一行合并 */
    private static final Pattern LABEL_LINE = Pattern.compile("^[^：:]{1,12}[：:]");
    /** 页码行形态（不含裸数字，裸数字走序列判定，避免误删表格里的数量列） */
    private static final List<Pattern> PAGE_NO_PATTERNS = List.of(
            Pattern.compile("^第\\s*[0-9]{1,4}\\s*页(\\s*[,，/]?\\s*共\\s*[0-9]{1,4}\\s*页)?$"),
            Pattern.compile("^共\\s*[0-9]{1,4}\\s*页$"),
            Pattern.compile("^[-—－–]?\\s*[0-9]{1,4}\\s*[-—－–]?\\s*/\\s*[0-9]{1,4}\\s*$"),
            Pattern.compile("^[-—－–]\\s*[0-9]{1,4}\\s*[-—－–]$"),
            Pattern.compile("^page\\s*[0-9]{1,4}(\\s*(of|/)\\s*[0-9]{1,4})?$", Pattern.CASE_INSENSITIVE));
    /** 裸数字行（可能是页码，也可能是表格数量；只有成序列时才删） */
    private static final Pattern BARE_NUMBER = Pattern.compile("^[0-9]{1,4}$");
    /** 句末/收尾标点：上一行以这些字符结尾时视为完整句子，不与下一行合并 */
    private static final String SENTENCE_END = "。！？；：.!?;:…）】》」』\"'”’";
    /** 句读标点：出现在行内即说明是句子而非表格行（表格行只由词组构成） */
    private static final Pattern SENTENCE_PUNCTUATION = Pattern.compile("[。！？；：，、,.!?;:]");
    /** 水印/扫描仪厂商字样 */
    private static final String[] WATERMARK_TOKENS = {
            "扫描全能王", "camscanner", "adobe acrobat", "foxit", "nitropdf", "pdf24", "smallpdf"
    };

    private BidTextCleaner() {
    }

    /**
     * 清洗入库全文（索引阶段唯一入口）。
     *
     * @param raw            Tika 抽取或纯文本直传的原始全文
     * @param sourceDocName  来源文档名（用于识别与文档名同名的页眉）
     * @return 清洗后的文本 + 统计；入参为空时返回空结果
     */
    public static Result clean(String raw, String sourceDocName) {
        Counter counter = new Counter();
        if (raw == null || raw.isEmpty()) {
            return new Result("", 0, raw == null ? 0 : raw.length(), counter);
        }
        // 1) 编码统一（含换行统一、不可见字符清理、全角兼容归一）
        String text = normalizeEncoding(raw, counter);
        // 分页符 = 段落边界（Tika 解析 PDF/DOCX 分页处会留下 \f）
        text = text.replace('\f', '\n');

        // 2) 逐行去噪：页码/水印/装饰行直接删，裸数字先留下做序列判定
        String docName = sourceDocName == null ? "" : normalizeLine(sourceDocName);
        String[] rawLines = text.split("\n", -1);
        List<String> kept = new ArrayList<>(rawLines.length);
        List<Boolean> bareNumbers = new ArrayList<>(rawLines.length);
        Map<String, Integer> lineCounts = new HashMap<>();
        for (String rawLine : rawLines) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty()) {
                counter.blankLines++;
                continue;
            }
            if ((!docName.isEmpty() && line.equals(docName)) || isWatermark(line)) {
                counter.noiseLines++;
                counter.sample("水印/页眉", line);
                continue;
            }
            if (isPageNoLine(line)) {
                counter.pageNoLines++;
                counter.sample("页码", line);
                continue;
            }
            if (BARE_NUMBER.matcher(line).matches()) {
                kept.add(line);
                bareNumbers.add(Boolean.TRUE);
                continue;
            }
            if (PURE_DECORATION.matcher(line).matches()) {
                counter.noiseLines++;
                counter.sample("装饰行", line);
                continue;
            }
            lineCounts.merge(line, 1, Integer::sum);
            kept.add(line);
            bareNumbers.add(Boolean.FALSE);
        }

        // 3) 删页眉/页脚：短行且重复出现（占比达标）；条款行与填空占位行受保护
        List<String> filtered = new ArrayList<>(kept.size());
        for (int i = 0; i < kept.size(); i++) {
            String line = kept.get(i);
            if (!bareNumbers.get(i) && isRunningLine(line, lineCounts, kept.size())) {
                counter.noiseLines++;
                counter.sample("重复页眉", line);
                continue;
            }
            filtered.add(line);
        }
        // 4) 裸数字：只有构成递增序列（典型页码）时才删，表格数量列一律保留
        filtered = dropPageNumberSequence(filtered, counter);

        // 5) 结构归一：合并硬换行 → 去掉相邻重复段
        List<String> paragraphs = mergeWrappedLines(filtered, counter);
        paragraphs = dropAdjacentDuplicates(paragraphs, counter);

        String cleaned = String.join("\n", paragraphs);
        return new Result(cleaned, paragraphs.size(), raw.length(), counter);
    }

    /**
     * 检索词归一（检索侧与入库侧同口径）：只做编码统一，不做去噪与结构改写。
     *
     * <p>入库侧把全角字母数字折成半角、清掉零宽字符后，检索词必须同样处理，
     * 否则"２０２６"这类写法会与入库文本对不上，召回凭空变低。
     */
    public static String normalizeEncoding(String raw) {
        return normalizeEncoding(raw, new Counter());
    }

    private static String normalizeEncoding(String raw, Counter counter) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace('\t', ' ');
        text = INVISIBLE.matcher(text).replaceAll("");
        text = PRIVATE_USE.matcher(text).replaceAll("");
        text = REPLACEMENT_CHAR.matcher(text).replaceAll("");
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = INVISIBLE_SPACE.matcher(text).replaceAll(" ");
        return foldCompatibility(text, counter);
    }

    /**
     * 逐码点做 NFKC 兼容归一，但保留中文标点。
     *
     * <p>保留清单（不改写，避免把中文排版折成英文标点）：CJK 标点 {@code 、。〈〉《》「」『』【】〔〕}、
     * 全角 {@code ！ （ ） ， ： ； ？}、中文引号与破折号省略号 {@code “”‘’—…}、间隔号 {@code ·・}。
     */
    private static String foldCompatibility(String text, Counter counter) {
        StringBuilder sb = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isCjkPunctuation(codePoint)) {
                sb.appendCodePoint(codePoint);
                continue;
            }
            String folded = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFKC);
            if (!folded.equals(new String(Character.toChars(codePoint)))) {
                counter.foldedChars++;
            }
            sb.append(folded);
        }
        return sb.toString();
    }

    private static boolean isCjkPunctuation(int cp) {
        if (cp == 0x3001 || cp == 0x3002 || cp == 0x30FB || cp == 0x00B7) {
            return true;                                    // 、。・·
        }
        if (cp >= 0x3008 && cp <= 0x301B) {
            return true;                                    // 〈〉《》「」『』【】〔〕〖〗
        }
        if (cp >= 0x301D && cp <= 0x301F) {
            return true;                                    // 〝〞〟
        }
        if (cp == 0xFF01 || cp == 0xFF08 || cp == 0xFF09
                || cp == 0xFF0C || cp == 0xFF1A || cp == 0xFF1B || cp == 0xFF1F) {
            return true;                                    // ！（） ， ： ； ？
        }
        if (cp == 0x2014 || cp == 0x2026) {
            return true;                                    // — …
        }
        return cp >= 0x2018 && cp <= 0x201D;                // ‘’“”
    }

    /** 单行归一：去首尾空白 → 行内连续空白压成单空格 → 目录点线折省略号 → 装饰符压缩 */
    private static String normalizeLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return "";
        }
        line = WHITESPACE_RUN.matcher(line).replaceAll(" ");
        line = DOT_LEADER.matcher(line).replaceAll("……");
        line = DECORATION_RUN.matcher(line).replaceAll("$1");
        return line.trim();
    }

    private static boolean isWatermark(String line) {
        if (line.length() > RUNNING_LINE_MAX_LEN) {
            return false;
        }
        String lower = line.toLowerCase();
        for (String token : WATERMARK_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPageNoLine(String line) {
        for (Pattern pattern : PAGE_NO_PATTERNS) {
            if (pattern.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    /** 页眉/页脚判定：短行 + 重复出现 + 占比达标；条款行与填空占位行不删 */
    private static boolean isRunningLine(String line, Map<String, Integer> lineCounts, int lineTotal) {
        if (line.length() > RUNNING_LINE_MAX_LEN || STRUCTURE_START.matcher(line).find()
                || PLACEHOLDER_LINE.matcher(line).find()) {
            return false;
        }
        Integer count = lineCounts.get(line);
        if (count == null || count < RUNNING_LINE_MIN_COUNT) {
            return false;
        }
        return count >= lineTotal * RUNNING_LINE_MIN_RATIO;
    }

    /**
     * 裸数字页码序列判定：只有"孤立出现 + 整体非递减"的裸数字才判定为页码并删除。
     *
     * <p>孤立（前后相邻行都不是裸数字）排除表格数量列——列数据是连续数字行；
     * 非递减排除随机数字。两条同时满足才删，宁可漏删页码也不误删数据。
     */
    private static List<String> dropPageNumberSequence(List<String> lines, Counter counter) {
        boolean[] bareFlags = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            bareFlags[i] = BARE_NUMBER.matcher(lines.get(i)).matches();
        }
        List<Integer> positions = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!bareFlags[i]) {
                continue;
            }
            boolean isolated = (i == 0 || !bareFlags[i - 1]) && (i == lines.size() - 1 || !bareFlags[i + 1]);
            if (!isolated) {
                continue;
            }
            positions.add(i);
            values.add(Integer.valueOf(lines.get(i)));
        }
        if (positions.size() < PAGE_SEQ_MIN_SIZE) {
            return lines;
        }
        int ascending = 0;
        for (int i = 1; i < values.size(); i++) {
            int step = values.get(i) - values.get(i - 1);
            if (step >= 0 && step <= PAGE_SEQ_MAX_STEP) {
                ascending++;
            }
        }
        if (ascending < (values.size() - 1) * PAGE_SEQ_MIN_ASCENT_RATIO) {
            return lines;
        }
        List<String> result = new ArrayList<>(lines.size() - positions.size());
        int cursor = 0;
        for (int position : positions) {
            while (cursor < position) {
                result.add(lines.get(cursor++));
            }
            cursor = position + 1;
            counter.pageNoLines++;
            counter.sample("页码序列", lines.get(position));
        }
        while (cursor < lines.size()) {
            result.add(lines.get(cursor++));
        }
        return result;
    }

    /** 合并硬换行：上一行够长且未以句末标点收尾、下一行不是新条款/新标题时，把折行接回同一段 */
    private static List<String> mergeWrappedLines(List<String> lines, Counter counter) {
        List<String> paragraphs = new ArrayList<>(lines.size());
        String current = null;
        for (String line : lines) {
            if (current == null) {
                current = line;
                continue;
            }
            if (shouldMergeWrapped(current, line)) {
                current = joinWrapped(current, line);
                counter.mergedWraps++;
                continue;
            }
            paragraphs.add(current);
            current = line;
        }
        if (current != null) {
            paragraphs.add(current);
        }
        return paragraphs;
    }

    private static boolean shouldMergeWrapped(String previous, String next) {
        if (previous.length() < WRAP_MERGE_MIN_LINE_LEN) {
            return false;
        }
        if (previous.length() + next.length() > WRAP_MERGE_MAX_LINE_LEN) {
            return false;
        }
        if (SENTENCE_END.indexOf(previous.charAt(previous.length() - 1)) >= 0) {
            return false;
        }
        if (looksLikeTableRow(next)) {
            return false;
        }
        if (BARE_NUMBER.matcher(next).matches()) {
            return false;                                   // 裸数字（页码/数量）自成一行，不吞进上一行
        }
        return !STRUCTURE_START.matcher(next).find() && !LABEL_LINE.matcher(next).find();
    }

    /** 表格行特征：3 个以上空白分隔的短词且无句读标点（PDF 表格逐格抽取的典型形态）→ 不合并 */
    private static boolean looksLikeTableRow(String line) {
        if (SENTENCE_PUNCTUATION.matcher(line).find()) {
            return false;
        }
        String[] tokens = line.split(" ");
        if (tokens.length < 3) {
            return false;
        }
        for (String token : tokens) {
            if (token.length() > TABLE_TOKEN_MAX_LEN) {
                return false;
            }
        }
        return true;
    }

    /** 折行拼接：两侧都是 ASCII 字母数字时补一个空格，中文按原样直接接 */
    private static String joinWrapped(String previous, String next) {
        char last = previous.charAt(previous.length() - 1);
        char first = next.charAt(0);
        boolean needSpace = isAsciiWordChar(last) && isAsciiWordChar(first);
        return needSpace ? previous + " " + next : previous + next;
    }

    private static boolean isAsciiWordChar(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    private static List<String> dropAdjacentDuplicates(List<String> paragraphs, Counter counter) {
        List<String> result = new ArrayList<>(paragraphs.size());
        String previous = null;
        for (String paragraph : paragraphs) {
            if (paragraph.equals(previous)) {
                counter.duplicateLines++;
                continue;
            }
            result.add(paragraph);
            previous = paragraph;
        }
        return result;
    }

    /** 清洗过程中的计数（写日志与复核用） */
    private static final class Counter {

        /** 被删行的抽样（最多 8 条），供日志复核"到底删了什么" */
        private static final int SAMPLE_LIMIT = 8;

        private int blankLines;
        private int noiseLines;
        private int pageNoLines;
        private int duplicateLines;
        private int mergedWraps;
        private int foldedChars;
        private final List<String> samples = new ArrayList<>();

        private void sample(String reason, String line) {
            if (samples.size() >= SAMPLE_LIMIT) {
                return;
            }
            samples.add(reason + ":" + (line.length() > 40 ? line.substring(0, 40) + "…" : line));
        }
    }

    /** 清洗结果：清洗后文本 + 参数统计（可复算，便于回答"这份资料到底被改了什么"） */
    public static final class Result {

        private final String text;
        private final int paragraphs;
        private final int inputChars;
        private final Counter counter;

        private Result(String text, int paragraphs, int inputChars, Counter counter) {
            this.text = text;
            this.paragraphs = paragraphs;
            this.inputChars = inputChars;
            this.counter = counter;
        }

        public String getText() {
            return text;
        }

        public int getParagraphs() {
            return paragraphs;
        }

        public int getInputChars() {
            return inputChars;
        }

        public int getOutputChars() {
            return text.length();
        }

        public int getDroppedBlankLines() {
            return counter.blankLines;
        }

        public int getDroppedNoiseLines() {
            return counter.noiseLines;
        }

        public int getDroppedPageNoLines() {
            return counter.pageNoLines;
        }

        public int getDroppedDuplicateLines() {
            return counter.duplicateLines;
        }

        public int getMergedWraps() {
            return counter.mergedWraps;
        }

        public int getFoldedChars() {
            return counter.foldedChars;
        }

        /** 被删行的抽样（"理由:原文"），用于复核去噪没有误删正文 */
        public List<String> getDroppedSamples() {
            return counter.samples;
        }

        /** 一行摘要，直接写进入库日志 */
        public String summary() {
            return "chars=" + inputChars + "->" + getOutputChars()
                    + ", paragraphs=" + paragraphs
                    + ", 删页码=" + getDroppedPageNoLines()
                    + ", 删页眉页脚水印=" + getDroppedNoiseLines()
                    + ", 删空行=" + getDroppedBlankLines()
                    + ", 删重复段=" + getDroppedDuplicateLines()
                    + ", 合并折行=" + getMergedWraps()
                    + ", 编码归一字符=" + getFoldedChars();
        }
    }
}
