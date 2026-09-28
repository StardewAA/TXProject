package cn.iocoder.txgy.module.bidding.service.bidagent.index;

// [依据] 切片重叠（2026-09-21）：答案常落在切分点两侧，相邻片段保留 10% 重叠，
//       保证跨边界内容至少在一个片段里是完整的；重叠只调名额，不丢内容。

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切分逻辑单测：只测纯函数 splitContent（不依赖 Spring / 向量库 / 租户上下文）。
 */
class BidIndexServiceImplTest {

    private static final int CHUNK_SIZE = 100;
    private static final int OVERLAP = 10;

    @Test
    void splitContent_keepsOverlapBetweenAdjacentChunks() {
        List<String> chunks = BidIndexServiceImpl.splitContent(paragraphs(30), CHUNK_SIZE, OVERLAP);

        assertTrue(chunks.size() > 3, "语料应被切成多片：" + chunks.size());
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String tail = previous.substring(Math.max(0, previous.length() - OVERLAP));
            int start = 0;
            while (start < tail.length() && Character.isWhitespace(tail.charAt(start))) {
                start++;
            }
            assertTrue(chunks.get(i).startsWith(tail.substring(start)),
                    "第 " + i + " 片应以第 " + (i - 1) + " 片尾部重叠开头");
        }
    }

    @Test
    void splitContent_withoutOverlapKeepsChunksDisjoint() {
        List<String> chunks = BidIndexServiceImpl.splitContent(paragraphs(30), CHUNK_SIZE, 0);

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String tail = previous.substring(Math.max(0, previous.length() - OVERLAP));
            assertFalse(chunks.get(i).startsWith(tail), "overlap=0 时不应带上一片段尾部");
        }
    }

    @Test
    void splitContent_keepsEveryParagraphSomewhere() {
        String joined = String.join("\n", BidIndexServiceImpl.splitContent(paragraphs(30), CHUNK_SIZE, OVERLAP));

        for (int i = 0; i < 30; i++) {
            assertTrue(joined.contains("第" + i + "条"), "第" + i + "条不应在切分中丢失");
        }
    }

    @Test
    void splitContent_ignoresBlankLinesAndSingleShortParagraph() {
        assertEquals(List.of("只有一段"), BidIndexServiceImpl.splitContent("\n\n只有一段\n\n", CHUNK_SIZE, OVERLAP));
    }

    /** 造若干段可切分的正文（每段约 30 字），便于验证切分与重叠 */
    private static String paragraphs(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append("第").append(i).append("条 本条款约定防腐保温施工的质量要求与验收标准及违约责任。").append('\n');
        }
        return builder.toString();
    }
}
