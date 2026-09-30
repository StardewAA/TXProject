package cn.iocoder.txgy.module.cms.service.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;
import static org.assertj.core.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void lawDefaultsToArticleRegexAndKeepsArticleBoundaries() {
        String text = "中华人民共和国测试法\n第一条 第一条正文。\n第二条 第二条正文。\n第三条 第三条正文。";
        var chunks = service.split(text, null, Kind.LAW);
        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(1).text()).startsWith("第一条");
        assertThat(chunks.get(2).text()).startsWith("第二条");
        assertThat(chunks).extracting(Chunk::ordinal).containsExactly(1, 2, 3, 4);
    }

    @Test
    void fixedStrategySupportsOverlap() {
        String text = "甲".repeat(900);
        ChunkingOptions options = new ChunkingOptions(KnowledgeCategory.QA, ChunkStrategy.FIXED, 400, 50, null);
        var chunks = service.split(text, options, Kind.POLICY);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(1).startOffset()).isEqualTo(350);
    }

    @Test
    void previewOnlyReadsFirstTwoThousandCharactersAndReturnsFiveSamples() {
        String text = ("第一条 内容。\n第二条 内容。\n第三条 内容。\n").repeat(200);
        ChunkPreview preview = service.preview(text, null, Kind.LAW);
        assertThat(preview.samples()).hasSizeLessThanOrEqualTo(5);
        assertThat(preview.samples()).allMatch(chunk -> chunk.endOffset() <= 2000);
        assertThat(preview.resolvedOptions().category()).isEqualTo(KnowledgeCategory.LAW);
    }

    @Test
    void policyDefaultsToIndependentFinancePolicyCategory() {
        ChunkingOptions options = service.resolve(null, Kind.POLICY);
        assertThat(options.category()).isEqualTo(KnowledgeCategory.FINANCE_POLICY);
        assertThat(options.strategy()).isEqualTo(ChunkStrategy.SEMANTIC);
    }

    @Test
    void rejectsNestedQuantifierRegex() {
        ChunkingOptions options = new ChunkingOptions(KnowledgeCategory.LAW, ChunkStrategy.REGEX,
                800, 0, "(a+)+");
        assertThatThrownBy(() -> service.resolve(options, Kind.LAW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriesPdfPageNumberIntoChunks() {
        String text = "第一条 第一页内容。\n第二条 第二页内容。";
        int secondPage = text.indexOf("第二条");
        var chunks = service.split(text, null, Kind.LAW, List.of(
                new PageSpan(1, 0, secondPage),
                new PageSpan(2, secondPage, text.length())));
        assertThat(chunks).extracting(Chunk::pageNumber).containsExactly(1, 2);
    }
}
