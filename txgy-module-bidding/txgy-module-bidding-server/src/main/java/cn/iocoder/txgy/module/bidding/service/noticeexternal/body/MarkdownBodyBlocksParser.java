package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;
// 规范依据：联调清单 R12② 策略链兜底档容错：AI 排版输出可能是 JSON blocks（优先），
// 也可能是 Markdown（模型"排版"心智的自然输出，含 md 表格）——后者在此转回统一块 schema。

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → 结构化块 转换（AI 兜底输出的 md 表格/标题/段落归一到 BodyBlock schema）。
 *
 * <p>支持：ATX 标题（#~######）、GitHub 管道表格（含 |---|---| 分隔行）、
 * 无序列表（- 或 * 或 +）、有序列表（数字.）、普通段落；行内 md 记号（粗体双星号、反引号）剥除，
 * 空行分段。无法识别的内容并入段落不丢。
 */
public final class MarkdownBodyBlocksParser {

    private static final Pattern ATX = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern UNORDERED = Pattern.compile("^[-*+]\\s+(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d+[.、)]\\s+(.+)$");
    private static final Pattern PIPE_ROW = Pattern.compile("^\\|.*\\|\\s*$");
    private static final Pattern SEP_ROW = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?$");
    private static final Pattern FENCE = Pattern.compile("^```");

    private MarkdownBodyBlocksParser() {
    }

    public static List<BodyBlock> parse(String markdown) {
        List<BodyBlock> blocks = new ArrayList<>();
        if (!StringUtils.hasText(markdown)) {
            return blocks;
        }
        StringBuilder paragraph = new StringBuilder();
        List<String> tableLines = new ArrayList<>();
        List<String> listItems = new ArrayList<>();
        boolean[] orderedList = {false};
        boolean inFence = false;

        for (String rawLine : markdown.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (inFence) {
                if (FENCE.matcher(line).find()) {
                    inFence = false;
                }
                continue;
            }
            if (FENCE.matcher(line).find()) {
                flushAll(blocks, paragraph, tableLines, listItems, orderedList);
                inFence = true;
                continue;
            }
            if (line.isEmpty()) {
                flushAll(blocks, paragraph, tableLines, listItems, orderedList);
                continue;
            }
            Matcher atx = ATX.matcher(line);
            if (atx.matches()) {
                flushAll(blocks, paragraph, tableLines, listItems, orderedList);
                blocks.add(BodyBlock.heading(atx.group(1).length(), clean(atx.group(2))));
                continue;
            }
            if (PIPE_ROW.matcher(line).matches()) {
                flushParagraph(blocks, paragraph);
                flushList(blocks, listItems, orderedList);
                tableLines.add(line);
                continue;
            }
            if (!tableLines.isEmpty()) {
                blocks.add(tableBlock(tableLines));
                tableLines.clear();
            }
            Matcher unordered = UNORDERED.matcher(line);
            if (unordered.matches()) {
                flushParagraph(blocks, paragraph);
                if (listItems.isEmpty()) {
                    orderedList[0] = false;
                }
                listItems.add(clean(unordered.group(1)));
                continue;
            }
            Matcher ordered = ORDERED.matcher(line);
            if (ordered.matches()) {
                flushParagraph(blocks, paragraph);
                if (listItems.isEmpty()) {
                    orderedList[0] = true;
                }
                listItems.add(clean(ordered.group(1)));
                continue;
            }
            flushList(blocks, listItems, orderedList);
            if (paragraph.length() > 0 && !paragraph.toString().endsWith(" ")) {
                paragraph.append(' ');
            }
            paragraph.append(clean(line));
        }
        flushAll(blocks, paragraph, tableLines, listItems, orderedList);
        return blocks;
    }

    private static BodyBlock tableBlock(List<String> lines) {
        String headerLine = lines.get(0);
        List<String> cells = splitCells(headerLine);
        boolean hasSeparator = lines.size() > 1 && SEP_ROW.matcher(lines.get(1)).matches();
        List<List<String>> rows = new ArrayList<>();
        int bodyStart = hasSeparator ? 2 : 1;
        for (int i = bodyStart; i < lines.size(); i++) {
            List<String> row = splitCells(lines.get(i));
            if (row.size() == cells.size() && row.stream().allMatch(StringUtils::hasText)) {
                rows.add(row);
            }
        }
        // 无数据行且无分隔行 → 首行即数据（无表头表）
        if (rows.isEmpty() && !hasSeparator) {
            List<String> first = splitCells(headerLine);
            if (first.stream().allMatch(StringUtils::hasText)) {
                return BodyBlock.table(null, List.of(first));
            }
        }
        return BodyBlock.table(cells, rows);
    }

    private static List<String> splitCells(String line) {
        String inner = line.trim();
        if (inner.startsWith("|")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) {
            cells.add(clean(cell));
        }
        return cells;
    }

    /** 剥行内 md 记号（** 粗体 / 反引号 / 残留 * 与链接语法保守剥 [x](y)→x） */
    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        t = t.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        t = t.replaceAll("`([^`]*)`", "$1");
        t = t.replace("**", "");
        t = t.replaceAll("\\[(.+?)\\]\\([^)]*\\)", "$1");
        return t.trim();
    }

    private static void flushAll(List<BodyBlock> blocks, StringBuilder paragraph,
                                 List<String> tableLines, List<String> listItems, boolean[] orderedList) {
        flushParagraph(blocks, paragraph);
        flushList(blocks, listItems, orderedList);
        if (!tableLines.isEmpty()) {
            blocks.add(tableBlock(tableLines));
            tableLines.clear();
        }
    }

    private static void flushList(List<BodyBlock> blocks, List<String> listItems, boolean[] orderedList) {
        if (!listItems.isEmpty()) {
            blocks.add(BodyBlock.list(orderedList[0], new ArrayList<>(listItems)));
            listItems.clear();
        }
    }

    private static void flushParagraph(List<BodyBlock> blocks, StringBuilder paragraph) {
        String text = paragraph.toString().trim();
        paragraph.setLength(0);
        if (StringUtils.hasText(text)) {
            blocks.add(BodyBlock.paragraph(text));
        }
    }

}
