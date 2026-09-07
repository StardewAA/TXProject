package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] JSON 公告载荷抽取（站点 JSON API/导出文件）：宽容字段映射（键名小写去分隔符后按别名集命中），
//       支持 {data:{...}} 等包装与数组取首元素；命中/未命中均进 meta，便于测试时校准键名。

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON → 公告字段抽取（通用策略 JSON_GENERIC 与站点专用策略共用）。
 */
public final class JsonNoticeExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
    };

    private static final Map<String, List<String>> FIELD_ALIASES = new HashMap<>();

    static {
        FIELD_ALIASES.put("title", List.of("title", "noticetitle", "announcementtitle", "公告标题", "公告名称", "标题"));
        FIELD_ALIASES.put("origUrl", List.of("origurl", "pageurl", "原文链接", "原网址", "detailurl"));
        FIELD_ALIASES.put("contentHtml", List.of("contenthtml", "htmlcontent", "正文html"));
        FIELD_ALIASES.put("contentText", List.of("content", "html", "body", "text", "正文", "内容", "公告内容"));
        FIELD_ALIASES.put("noticeTypeRaw", List.of("noticetyperaw", "noticetype", "announcetype", "公告类型", "公告类别"));
        FIELD_ALIASES.put("projectCode", List.of("projectcode", "projectno", "项目编号", "招标编号", "采购编号", "zb编号"));
        FIELD_ALIASES.put("tenderProjectCode", List.of("tenderprojectcode", "tenderprojectno", "招标项目编号"));
        FIELD_ALIASES.put("bidSectionCodes", List.of("bidsectioncodes", "sectioncodes", "标段编号", "标段号"));
        FIELD_ALIASES.put("projectName", List.of("projectname", "项目名称"));
        FIELD_ALIASES.put("issuerName", List.of("issuername", "publisher", "orgname", "发布单位", "招标人", "采购人", "建设单位", "招标单位"));
        FIELD_ALIASES.put("publishTime", List.of("publishtime", "publishdate", "issuedate", "noticetime", "发布时间", "发布日期", "公告时间"));
        FIELD_ALIASES.put("legalFlag", List.of("legalflag", "依法必招"));
        FIELD_ALIASES.put("effectiveFrom", List.of("effectivefrom", "生效开始"));
        FIELD_ALIASES.put("effectiveTo", List.of("effectiveto", "生效截止"));
    }

    private JsonNoticeExtractor() {
    }

    /**
     * 解析 JSON 文本并填充 out；语法错误 → FAILED；字段映射宽容。
     */
    public static void extract(String json, String url, NoticeParseResult out) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            out.setError("JSON 解析失败：" + messageOf(e));
            out.setStatus(NoticeParseResult.Status.FAILED);
            return;
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            out.setError("JSON 为空");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return;
        }
        // 载荷定位：对象包装字段（data/result/content/item/record/rows）或数组取首元素
        JsonNode payload = root;
        String location = "root";
        if (root.isArray()) {
            if (root.isEmpty()) {
                out.setError("JSON 数组为空，无公告数据");
                out.setStatus(NoticeParseResult.Status.FAILED);
                return;
            }
            payload = root.get(0);
            location = "array[0]";
            out.getWarnings().add("JSON 根为数组，已取首个元素解析");
        } else if (root.isObject()) {
            JsonNode wrapped = null;
            String wrappedKey = null;
            for (String key : new String[]{"data", "result", "content", "item", "record", "rows", "list"}) {
                JsonNode node = root.get(key);
                if (node != null && (node.isObject() || node.isArray()) && !node.isEmpty()) {
                    wrapped = node;
                    wrappedKey = key;
                    break;
                }
            }
            if (wrapped != null) {
                if (wrapped.isArray()) {
                    payload = wrapped.isEmpty() ? wrapped : wrapped.get(0);
                    location = wrappedKey + "[0]";
                    out.getWarnings().add("JSON 载荷位于字段 '" + wrappedKey + "'（数组已取首个元素）");
                } else {
                    payload = wrapped;
                    location = wrappedKey;
                }
            }
        }
        if (!payload.isObject()) {
            out.setError("JSON 载荷不是对象（无法映射公告字段），location=" + location);
            out.setStatus(NoticeParseResult.Status.FAILED);
            return;
        }
        // 字段映射（别名集 → normalized 键比较）
        Map<String, String> normKeys = new HashMap<>();
        payload.fieldNames().forEachRemaining(name -> normKeys.put(normalize(name), name));
        for (Map.Entry<String, List<String>> entry : FIELD_ALIASES.entrySet()) {
            String field = entry.getKey();
            for (String alias : entry.getValue()) {
                String actual = normKeys.get(normalize(alias));
                if (actual != null) {
                    JsonNode value = payload.get(actual);
                    assign(out, field, value);
                    out.getMeta().put("matched." + field, actual + "(" + alias + ")");
                    break;
                }
            }
            if (!out.getMeta().containsKey("matched." + field) && !"legalFlag".equals(field)
                    && !"effectiveFrom".equals(field) && !"effectiveTo".equals(field)
                    && !"tenderProjectCode".equals(field)) {
                out.getMeta().put("unmatched." + field, aliasHint(field));
            }
        }
        // 附件候选（attachments/files/附件/fileList 数组）
        int attachmentCount = 0;
        for (String key : new String[]{"attachments", "files", "filelist", "附件", "enclosures"}) {
            JsonNode actualNode = payload.get(key);
            if (actualNode == null) {
                actualNode = root.get(key);
            }
            if (actualNode != null && actualNode.isArray()) {
                for (JsonNode item : actualNode) {
                    if (!item.isObject()) {
                        continue;
                    }
                    String name = textOf(firstOf(item, "name", "filename", "fileName", "title"));
                    String href = textOf(firstOf(item, "url", "fileUrl", "downloadUrl", "href", "link"));
                    if (href == null && name == null) {
                        continue;
                    }
                    out.getAttachments().add(NoticeParseResult.AttachmentCandidate.of(
                            name == null ? lastSegment(href) : name, href, href == null));
                    attachmentCount++;
                }
                break;
            }
        }
        out.getMeta().put("payloadLocation", location);
        out.getMeta().put("rootKeyCount", String.valueOf(payload.size()));
        out.getMeta().put("attachmentCount", String.valueOf(attachmentCount));
        if (out.getContentHtml() == null && "HTML".equalsIgnoreCase(out.getContentType())) {
            // noop：contentType 由 content 形态推断
        }
    }

    private static void assign(NoticeParseResult out, String field, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return;
        }
        switch (field) {
            case "title" -> out.setTitle(scalarText(value));
            case "origUrl" -> out.setOrigUrl(scalarText(value));
            case "contentHtml" -> {
                out.setContentHtml(scalarText(value));
                out.setContentType("HTML");
            }
            case "contentText" -> {
                if (out.getContentText() == null) {
                    out.setContentText(scalarText(value));
                    if (out.getContentType() == null) {
                        out.setContentType("HTML");
                    }
                }
            }
            case "noticeTypeRaw" -> out.setNoticeTypeRaw(scalarText(value));
            case "legalFlag" -> out.setLegalFlag(scalarText(value));
            case "projectCode" -> out.setProjectCode(scalarText(value));
            case "tenderProjectCode" -> out.setTenderProjectCode(scalarText(value));
            case "bidSectionCodes" -> out.setBidSectionCodes(joinSections(value));
            case "projectName" -> out.setProjectName(scalarText(value));
            case "issuerName" -> out.setIssuerName(scalarText(value));
            case "publishTime" -> out.setPublishTime(parseTime(value, out));
            case "effectiveFrom" -> out.setEffectiveFrom(parseTime(value, out));
            case "effectiveTo" -> out.setEffectiveTo(parseTime(value, out));
            default -> {
            }
        }
    }

    private static String aliasHint(String field) {
        List<String> aliases = FIELD_ALIASES.get(field);
        return aliases == null || aliases.isEmpty() ? "" : aliases.get(0);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[-_ ]", "");
    }

    private static String scalarText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.isContainerNode() ? node.toString() : null;
    }

    private static String joinSections(JsonNode node) {
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            node.forEach(item -> {
                String text = scalarText(item);
                if (text != null && !text.isBlank()) {
                    parts.add(text.trim());
                }
            });
            return parts.isEmpty() ? null : String.join(";", parts);
        }
        String text = scalarText(node);
        return text == null ? null : text.trim();
    }

    private static LocalDateTime parseTime(JsonNode node, NoticeParseResult out) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                long millis = node.asLong();
                if (millis > 0 && millis < 10_000_000_000L) {
                    millis = millis * 1000; // epoch 秒 → 毫秒
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
            }
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            if (text.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                return LocalDateTime.parse(text);
            }
            for (DateTimeFormatter formatter : TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(text, formatter);
                } catch (DateTimeParseException ignored) {
                    // 尝试下一个格式
                }
            }
            out.getWarnings().add("时间字段无法识别格式：" + text);
        } catch (RuntimeException e) {
            out.getWarnings().add("时间字段解析异常：" + node.asText() + "（" + messageOf(e) + "）");
        }
        return null;
    }

    private static String textOf(JsonNode node) {
        return node == null ? null : node.asText();
    }

    private static JsonNode firstOf(JsonNode item, String... keys) {
        for (String key : keys) {
            JsonNode node = item.get(key);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private static String lastSegment(String url) {
        String cleaned = url == null ? "" : url.split("[?#]")[0];
        int slash = cleaned.lastIndexOf('/');
        String last = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return last.isEmpty() ? "附件" : last;
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

}
