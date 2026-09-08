package cn.iocoder.txgy.module.bidding.service.externalfetch;

// 规范依据：外部信息获取 v1.0 §五/§六（Redis Stream 轻信号消费端执行：收 taskId → 回查 payload → 下载/直采 → ingest 回写）；
//           正文文本/源文件由下载链路 FileApi 承载，AI 结构化复用 noticeexternal.ai（v1.0 §三）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 信息获取任务执行器（消费端：一条 task = 一次下载/解析/落库工作单元）。
 *
 * <p>输入：task 行（url + payloadJson 快照）。执行：按 payload 是否有全文/文件引用分派：
 * <ul>
 *   <li>payload 带 contentHtml/全文引用（QYZB 类列表直采）→ 组装 DTO 直采落库（无网络）；</li>
 *   <li>payload 仅有 URL/标题（GX/详情类）→ 保留 URL 由后续详情抓取链路（P2）处理，本轮标记 SKIPPED 语义由调用方决定。</li>
 * </ul>
 * 纯同步、无状态：生产端直接跑与消费者异步共用同一执行语义。
 */
@Slf4j
@Component
public class CrawlNoticeProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private ExternalNoticeIngestService externalNoticeIngestService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient httpFetchClient;

    /**
     * 执行一条信息获取任务（按 payload 快照落库）。
     *
     * @return 成功落库返回公告 id；缺执行素材（无全文且无详情 URL）抛 IllegalStateException 由调用方计失败
     */
    public Long execute(CrawlTaskDO task) throws Exception {
        if (task == null) {
            throw new IllegalStateException("任务不存在");
        }
        JsonNode payload = parsePayload(task.getPayloadJson());
        String title = text(payload, "title");
        String url = text(payload, "url");
        String date = text(payload, "date");
        String category = text(payload, "category");
        String contentHtml = text(payload, "contentHtml");
        if (!StringUtils.hasText(url)) {
            url = task.getUrl();
        }
        // 全文文件化：payload 无内联全文时按 rawFileUrl 从平台文件服务拉取（v1.0 §2.2 原文文件化）
        if (!StringUtils.hasText(contentHtml)) {
            String rawFileUrl = text(payload, "rawFileUrl");
            if (StringUtils.hasText(rawFileUrl)) {
                contentHtml = httpFetchClient.getText(rawFileUrl);
            }
        }
        if (!StringUtils.hasText(contentHtml)) {
            // 无全文：详情/正文由后续抓取链路承接（本次不落库，交由调用方计失败留痕）
            throw new IllegalStateException("任务无正文素材（全文直采缺失/详情链路未接线），url=" + url);
        }
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(task.getSourceCode());
        dto.setOrigUrl(url);
        dto.setTitle(StringUtils.hasText(title) ? title : null);
        dto.setContentType("HTML");
        dto.setContentHtml(contentHtml);
        dto.setNoticeTypeRaw(category);
        LocalDateTime publish = parseDate(date);
        dto.setPublishTime(publish == null ? LocalDateTime.now() : publish);
        dto.setIssuerName(extractIssuer(contentHtml));
        dto.setParserCode(task.getParserCode() == null ? task.getChannelCode() : task.getParserCode());
        return externalNoticeIngestService.ingest(dto);
    }

    private JsonNode parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            return MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull() || field == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String s = v.isTextual() ? v.asText() : v.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 宽松解析外站日期（多格式兜底；解析失败返回 null 由调用方取抓取时间） */
    private static LocalDateTime parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        // 去尾毫秒/时区偏移（yyyy-MM-ddTHH:mm:ss.SSS+0800 → 截断到秒）
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '+' || ch == '-' || ch == 'Z') {
                if (i > 8) {
                    cut = i;
                    break;
                }
            }
        }
        s = s.substring(0, cut).replace('T', ' ');
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)
        };
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignore) {
                // 尝试下一格式
            }
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length()))).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    /** 招标人/采购人启发式抽取（正文前 800 字符内的 “招标人|采购人：XXX”） */
    private static String extractIssuer(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return null;
        }
        try {
            String bodyText = org.jsoup.Jsoup.parse(contentHtml).body() == null ? "" : org.jsoup.Jsoup.parse(contentHtml).body().text();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(招标人|采购人)\\s*[:：]\\s*([^\\s]{2,60})").matcher(bodyText);
            if (matcher.find()) {
                String name = matcher.group(2).trim();
                int c = name.length();
                for (String marker : new String[]{"地", "联", "电", "，", ",", "。"}) {
                    int at = name.indexOf(marker);
                    if (at >= 0 && at < c) {
                        c = at;
                    }
                }
                return name.substring(0, Math.max(2, c)).trim();
            }
        } catch (Exception ignore) {
            // 抽取失败返回 null（不阻断）
        }
        return null;
    }
}