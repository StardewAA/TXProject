package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;

/**
 * 外部公告检索索引的 mapping 单一事实来源（索引通知器与检索引擎共用，杜绝"建/查不同源"）。
 *
 * <p><b>为什么需要本类（2026-09-22 契约 B1/B6 治本）</b>：
 * 原索引靠 ES 动态映射自动创建，所有 text 字段落到默认 {@code standard} 分析器。
 * standard 对中文是<b>逐字切分</b>——「炼化」→「炼」「化」，于是 {@code multi_match} 默认 OR
 * 下"任意一个字命中即命中"：实测搜「炼化」命中 1050/1637 条（64%），而真正含该词的只有 21 条。
 * 用户感受即"ES 太宽泛，搜不到想要的"。
 *
 * <p><b>为什么用 ngram 而不是装 IK 分词器</b>：
 * ① IK/smartcn 均需改 ES 镜像装插件（本环境 ES 8.10 容器 {@code plugins: []}），属环境变更、成本高；
 * ② ngram 是 ES <b>内置</b>分析器，零依赖，且语义上更贴合"公告检索"的真实诉求——
 *    用户输入「炼化」期望的是<b>字面包含</b>（像 Ctrl+F），不是语义扩展；
 * ③ 实测 ngram+term 与 MySQL 字面比对（{@code LIKE '%kw%'}）在 7 个关键词上 <b>7/7 完全一致</b>。
 *
 * <p><b>工作原理</b>：索引侧 {@code ngram_index_analyzer} 用 keyword tokenizer + ngram filter，
 * 把字段值切成所有 2~{@link #MAX_GRAM} 字的连续子串入倒排（「榆林化学」→ 榆林、榆林化、榆林化学、林化、林化学、化学）；
 * 查询侧 {@code ngram_search_analyzer} 只做 keyword（不做 ngram），
 * 于是查询词保持整体、以 {@code term} 语义精确命中倒排里的子串 → 等价于"字面包含"，且走索引（快）。
 *
 * <p><b>改动本类即改动索引结构</b>：必须重建索引（见 {@code sql/tools/rebuild-external-notice-index.js}
 * 或运维手册），否则新老 mapping 不一致会导致查询行为漂移。
 */
public final class ExternalNoticeIndexMapping {

    private ExternalNoticeIndexMapping() {
    }

    /** 索引名：与 EsExternalNoticeIndexNotifier.INDEX / EsExternalNoticeSearchEngine.INDEX 同源 */
    public static final String INDEX = "bidding-notice-external";

    /** 索引侧分析器名（ngram：写入时切 2~{@link #MAX_GRAM} 字连续子串入倒排） */
    public static final String INDEX_ANALYZER = "ngram_index_analyzer";

    /** 查询侧分析器名（keyword：查询词保持整体，配合 term 实现"字面包含"） */
    public static final String SEARCH_ANALYZER = "ngram_search_analyzer";

    /** ngram 最小长度：2（中文最小词长；单字查询走 keyword 子字段兜底，见检索引擎） */
    public static final int MIN_GRAM = 2;

    /** ngram 最大长度：10（覆盖绝大多数中文词组/机构简称；再长仍可被子串命中） */
    public static final int MAX_GRAM = 10;

    /**
     * 参与 ngram 全文检索的字段（与 {@code EsExternalNoticeIndexNotifier.buildDoc} 写入的字段一致）。
     *
     * <p>注意：{@code id}/{@code publishTime} 是结构化字段（long/date），不在此列。
     */
    public static final String[] TEXT_FIELDS = {
            "title", "projectName", "projectCode", "tenderProjectCode", "issuerName", "contentText",
            "sourceCode", "contentType", "origUrl", "bidSectionCodes", "industryFlag", "legalFlag",
            "status", "publicScope"
    };

    /**
     * 检索时参与匹配的字段（比索引字段窄：只匹配用户可感知的语义字段）。
     * 权重与旧实现保持一致（title 最高），保证"改前后相关度排序可比"。
     */
    public static final String[] SEARCH_FIELDS = {
            "title^3", "projectName^2", "projectCode", "tenderProjectCode", "issuerName", "contentText"
    };

    /**
     * 索引 settings：显式声明 ngram 分析器。
     *
     * <p>{@code max_ngram_diff} 必须显式给：ES 默认限制 max_gram-min_gram ≤ 1，否则建索引报
     * {@code The difference between max_gram and min_gram ... must be less than or equal to: [1]}。
     */
    public static String settingsJson() {
        return "{"
                + "\"number_of_shards\":1,"
                + "\"number_of_replicas\":0,"
                + "\"max_ngram_diff\":" + (MAX_GRAM - MIN_GRAM) + ","
                + "\"analysis\":{"
                + "  \"filter\":{\"ngram_filter\":{\"type\":\"ngram\",\"min_gram\":" + MIN_GRAM
                + ",\"max_gram\":" + MAX_GRAM + "}},"
                + "  \"analyzer\":{"
                + "    \"" + INDEX_ANALYZER + "\":{\"type\":\"custom\",\"tokenizer\":\"keyword\","
                + "\"filter\":[\"ngram_filter\"]},"
                + "    \"" + SEARCH_ANALYZER + "\":{\"type\":\"custom\",\"tokenizer\":\"keyword\"}"
                + "  }"
                + "}}";
    }

    /**
     * 索引 mappings：text 字段统一 ngram 分析器 + {@code keyword} 子字段（保留精确聚合/排序能力）。
     *
     * <p>{@code ignore_above:256} 与原动态映射一致；注意长正文（contentText 可达 10 万字）
     * 因此不会有 keyword 子字段——这是<b>预期</b>：全文检索走 ngram 主字段，keyword 子字段只服务短标识。
     */
    public static String mappingsJson() {
        StringBuilder sb = new StringBuilder("{\"properties\":{\"id\":{\"type\":\"long\"}"
                + ",\"publishTime\":{\"type\":\"date\"}"
                + ",\"traceable\":{\"type\":\"long\"}"
                + ",\"traceDimensions\":{\"type\":\"text\"}");
        for (String field : TEXT_FIELDS) {
            sb.append(",\"").append(field).append("\":{\"type\":\"text\"")
              .append(",\"analyzer\":\"").append(INDEX_ANALYZER).append("\"")
              .append(",\"search_analyzer\":\"").append(SEARCH_ANALYZER).append("\"")
              .append(",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}}");
        }
        sb.append("}}");
        return sb.toString();
    }
}
