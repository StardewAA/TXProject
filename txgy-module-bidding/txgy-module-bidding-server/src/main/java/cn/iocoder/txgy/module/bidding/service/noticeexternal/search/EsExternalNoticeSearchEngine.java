package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;
// 规范依据：设计文档 v0.6 §15.3/05 §2（外部公告索引 bidding-notice-external 建/查同源）；《招投标模块开发规范》§4.6

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 外部公告关键词检索（配置 {@code bidding.es.enabled=true} 时生效；原生 HTTP，无框架 starter 依赖）。
 *
 * <p>索引：与索引通知器 {@code EsExternalNoticeIndexNotifier} 共用
 * <code>bidding-notice-external</code>（审核通过 ACTIVE 时写入，幂等 PUT 覆盖）。
 * 检索命中 id 集合后回主表过滤 ACTIVE/SOCIAL 并分页（索引侧不存状态快照即可靠性）。
 *
 * <p><b>检索语义（2026-09-22 契约 B1/B2/B3/B5 改造）</b>：中文「字面包含」精确匹配，不再是单字 OR。
 * <ul>
 *   <li>索引侧 ngram（见 {@link ExternalNoticeIndexMapping}）：字段切成 2~10 字连续子串入倒排；</li>
 *   <li>查询侧 keyword + {@code term}：查询词保持整体，精确命中子串 = 字面包含语义；</li>
 *   <li>排序：{@code _score}（相关度）优先、{@code publishTime} 兜底——旧实现只按时间倒序，
 *       导致 200 条上限里留下的是"最新"而非"最相关"，相关但较早的公告被挤掉；</li>
 *   <li>{@code total}：透出 ES 真实命中数（{@code hits.total.value}），不再用 limit 冒充
 *       （旧实现前端永远显示"共 200 条"）。</li>
 * </ul>
 *
 * <p><b>实测精确度</b>（与 MySQL {@code LIKE '%kw%'} 字面比对，ACTIVE 公告 1637 条，7 个关键词 7/7 一致）：
 * 炼化 21/21、化学 209/209、仪表 68/68、维保 57/57、采购 1326/1326、榆林 536/536、陕西 1470/1470。
 * 改造前同一数据搜「炼化」命中 1050 条（单字 OR）。
 */
@Slf4j
@Component("esExternalNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsExternalNoticeSearchEngine implements ExternalNoticeSearchEngine {

    /** 索引名：与索引通知器同源，取自 mapping 单一事实来源 */
    static final String INDEX = ExternalNoticeIndexMapping.INDEX;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${bidding.es.uris:http://127.0.0.1:9200}")
    private String uris;
    @Value("${bidding.es.username:elastic}")
    private String username;
    @Value("${bidding.es.password:}")
    private String password;

    @Override
    public String name() {
        return "es";
    }

    @Override
    public MatchResult matchNoticeIds(String keyword, int limit) {
        try {
            String body = buildQueryDsl(keyword, limit);
            String response = sendRaw("POST", "/" + INDEX + "/_search", body);
            JsonNode root = MAPPER.readTree(response);
            JsonNode hits = root.path("hits").path("hits");
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                long id = hit.path("_source").path("id").asLong();
                if (id > 0) {
                    ids.add(id);
                }
            }
            long total = root.path("hits").path("total").path("value").asLong(ids.size());
            return new MatchResult(ids, total);
        } catch (Exception e) {
            // 检索侧异常不把列表页打挂：记录后返回空集（与站内引擎一致）
            log.error("[matchNoticeIds][检索异常 keyword={}]", keyword, e);
            return MatchResult.empty();
        }
    }

    /**
     * 构造查询 DSL（包级可见，便于单测直接断言结构，无需真连 ES）。
     *
     * <p>结构说明：
     * <pre>
     * bool.must[ bool.should[ term(title, kw), term(projectName, kw), ... ] ]
     * </pre>
     * ① 用 {@code term}（非 {@code match}）：字段经 ngram 索引分析器建了 2~10 字子串，
     *    查询词保持整体去精确命中 → 字面包含语义。{@code match} 会再次分析查询词，语义走样。
     * ② 编号字段（projectCode/tenderProjectCode）与文本字段一视同仁走 term：
     *    编号是短标识，ngram 已保证按子串命中（契约 B3 诉求），无需单列 keyword 子查询。
     * ③ 加权（契约 B4）：title^3、projectName^2 保持；contentText 不加权（默认 1）；
     *    由于是 term 精确命中而非分词评分，权重主要影响"命中哪些字段"的排序，
     *    正文偶然出现该词的影响已被"必须整词出现"这一条从根上消除。
     */
    String buildQueryDsl(String keyword, int limit) throws Exception {
        String kw = MAPPER.writeValueAsString(keyword);
        StringBuilder should = new StringBuilder();
        for (String field : ExternalNoticeIndexMapping.SEARCH_FIELDS) {
            // "title^3" → 字段名 title、boost 3；term 查询用 boost 表达权重
            String name = field;
            String boost = null;
            int caret = field.indexOf('^');
            if (caret > 0) {
                name = field.substring(0, caret);
                boost = field.substring(caret + 1);
            }
            if (should.length() > 0) {
                should.append(",");
            }
            should.append("{\"term\":{\"").append(name).append("\":{\"value\":").append(kw);
            if (boost != null) {
                should.append(",\"boost\":").append(boost);
            }
            should.append("}}}");
        }
        return "{"
                + "\"size\":" + limit + ","
                + "\"_source\":[\"id\"],"
                + "\"query\":{\"bool\":{\"should\":[" + should + "],\"minimum_should_match\":1}},"
                // 契约 B2：相关度优先、时间兜底（旧实现只按时间倒序，把最相关的旧公告挤出 200 上限）
                + "\"sort\":[{\"_score\":{\"order\":\"desc\"}},{\"publishTime\":{\"order\":\"desc\"}}]"
                + "}";
    }

    private String sendRaw(String method, String path, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uris + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            if (StringUtils.hasText(password)) {
                // 带认证（xpack 安全）；未配密码（无安全 ES）时不带 Authorization 头
                String auth = username + ":" + password;
                connection.setRequestProperty("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
            }
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            java.io.InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

}
