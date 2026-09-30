package cn.iocoder.txgy.module.cms.service.review;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

/** 可预览、可持久化参数的切片策略工厂。 */
@Service
public class ChunkingService {

    private static final String LAW_DELIMITER = "(?m)^\\s*第[一二三四五六七八九十百千万零〇0-9]+\\s*条";
    private static final Pattern HEADING = Pattern.compile(
            "(?m)^(?:#{1,6}\\s+.+|第[一二三四五六七八九十百千万零〇0-9]+[章节编部]\\s*.*)$");

    public ChunkPreview preview(String text, ChunkingOptions requested, Kind kind) {
        return preview(text, requested, kind, List.of());
    }

    public ChunkPreview preview(String text, ChunkingOptions requested, Kind kind, List<PageSpan> pages) {
        String head = text.substring(0, Math.min(text.length(), 2000));
        ChunkingOptions resolved = resolve(requested, kind);
        List<Chunk> chunks = applyPages(splitResolved(head, resolved), pages);
        return new ChunkPreview(chunks.size(), resolved, chunks.stream().limit(5).toList());
    }

    public List<Chunk> split(String text, ChunkingOptions requested, Kind kind) {
        return splitResolved(text, resolve(requested, kind));
    }

    public List<Chunk> split(String text, ChunkingOptions requested, Kind kind, List<PageSpan> pages) {
        return applyPages(splitResolved(text, resolve(requested, kind)), pages);
    }

    private static List<Chunk> applyPages(List<Chunk> chunks, List<PageSpan> pages) {
        if (pages == null || pages.isEmpty()) return chunks;
        return chunks.stream().map(chunk -> new Chunk(chunk.ordinal(), chunk.startOffset(), chunk.endOffset(),
                chunk.text(), pageFor(chunk.startOffset(), pages), chunk.aiReviewStatus(),
                chunk.aiReviewMessage(), chunk.manualEdited(), chunk.version())).toList();
    }

    private static Integer pageFor(int offset, List<PageSpan> pages) {
        for (PageSpan page : pages) {
            if (offset >= page.startOffset() && offset < page.endOffset()) return page.pageNumber();
        }
        return pages.getLast().pageNumber();
    }

    public ChunkingOptions resolve(ChunkingOptions requested, Kind kind) {
        KnowledgeCategory category = requested == null || requested.category() == null
                ? (kind == null ? KnowledgeCategory.LAW : categoryFor(kind)) : requested.category();
        ChunkStrategy strategy = requested == null || requested.strategy() == null
                ? (category == KnowledgeCategory.LAW ? ChunkStrategy.REGEX : ChunkStrategy.SEMANTIC)
                : requested.strategy();
        int size = requested == null || requested.chunkSize() == null ? 800 : requested.chunkSize();
        int overlap = requested == null || requested.overlap() == null ? 80 : requested.overlap();
        overlap = Math.min(overlap, Math.max(0, size / 2));
        String regex = requested == null ? null : cleanRegex(requested.delimiterRegex());
        if (strategy == ChunkStrategy.REGEX && (regex == null || regex.isBlank())) regex = LAW_DELIMITER;
        validateRegex(regex);
        return new ChunkingOptions(category, strategy, size, overlap, regex);
    }

    private List<Chunk> splitResolved(String text, ChunkingOptions options) {
        if (text == null || text.isBlank()) return List.of();
        return switch (options.strategy()) {
            case FIXED -> fixed(text, options.chunkSize(), options.overlap());
            case REGEX -> boundaries(text, Pattern.compile(options.delimiterRegex()), options.chunkSize());
            case HEADING -> boundaries(text, HEADING, options.chunkSize());
            case SEMANTIC -> semantic(text, options.chunkSize(), options.overlap());
        };
    }

    private List<Chunk> fixed(String text, int size, int overlap) {
        List<Chunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                if (boundary > start + size / 2) end = boundary + 1;
            }
            if (end > start && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            add(result, text, start, end);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return List.copyOf(result);
    }

    private List<Chunk> semantic(String text, int size, int overlap) {
        Pattern paragraphs = Pattern.compile("(?:\\r?\\n){2,}|(?<=。)(?=\\s*[^，。；！？]{6,})");
        Matcher matcher = paragraphs.matcher(text);
        List<Integer> breaks = new ArrayList<>();
        while (matcher.find()) breaks.add(matcher.end());
        return packByBreaks(text, breaks, size, overlap);
    }

    private List<Chunk> boundaries(String text, Pattern pattern, int fallbackSize) {
        Matcher matcher = pattern.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.start() > 0) starts.add(matcher.start());
        }
        if (starts.isEmpty()) return fixed(text, fallbackSize, Math.min(80, fallbackSize / 10));
        List<Chunk> result = new ArrayList<>();
        int start = 0;
        for (int next : starts) {
            if (next > start) add(result, text, start, next);
            start = next;
        }
        add(result, text, start, text.length());
        return List.copyOf(result);
    }

    private List<Chunk> packByBreaks(String text, List<Integer> breaks, int size, int overlap) {
        if (breaks.isEmpty()) return fixed(text, size, overlap);
        List<Chunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int desired = Math.min(text.length(), start + size);
            int end = desired;
            for (int value : breaks) {
                if (value > start && value <= desired) end = value;
                if (value > desired) break;
            }
            if (end <= start) end = desired;
            add(result, text, start, end);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return List.copyOf(result);
    }

    private void add(List<Chunk> result, String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (start >= end) return;
        String value = text.substring(start, end);
        ChunkReviewStatus status = value.indexOf('\uFFFD') >= 0 ? ChunkReviewStatus.FAIL
                : value.length() < 30 ? ChunkReviewStatus.WARN : ChunkReviewStatus.PASS;
        String message = status == ChunkReviewStatus.FAIL ? "切片包含乱码替换字符"
                : status == ChunkReviewStatus.WARN ? "切片过短，请人工确认上下文是否完整" : null;
        result.add(new Chunk(result.size() + 1, start, end, value, null, status, message, false, 0L));
    }

    private static KnowledgeCategory categoryFor(Kind kind) {
        if (kind == Kind.POLICY) return KnowledgeCategory.FINANCE_POLICY;
        return kind == Kind.LAW || kind == Kind.STANDARD ? KnowledgeCategory.LAW : KnowledgeCategory.QA;
    }

    private static String cleanRegex(String regex) {
        return regex == null ? null : regex.strip();
    }

    private static void validateRegex(String regex) {
        if (regex == null) return;
        if (regex.length() > 200 || regex.matches(".*\\([^)]*[+*][^)]*\\)[+*].*") || regex.matches(".*\\\\[1-9].*")) {
            throw new IllegalArgumentException("切片正则过于复杂");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("切片正则格式不正确", ex);
        }
    }
}
