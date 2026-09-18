package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;
// 规范依据：设计文档 v0.6 §7（AI 解析能力由 AI 模块承载）；联调清单 R12② 策略链兜底档（AI_FALLBACK）
// 租户隔离/鉴权/调用链复用 AiHttpSupport（与 ai 抽取同一语义：触发者租户自己的渠道/key）

import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 兜底正文排版：把拍平/粘连/脏结构正文恢复为结构化块（策略码 AI_FALLBACK）。
 *
 * <p>适用：常规解析器（{@link HtmlBodyBlocksParser}）产物不足 —— 正文无 table 但含"每行一条记录"
 * 式的粘连文本（候选/标段/金额等原表格信息被压成一段）。AI 按 schema 还原为块数组，
 * 由运营在管理端人工触发（不自动烧 token）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.ai", name = "enabled", havingValue = "true")
public class AiBodyStructurer {

    /** 正文内联上限（防超大文本撑爆请求体） */
    private static final int MAX_INLINE_TEXT = 120_000;

    /** 块 JSON schema：type ∈ heading/paragraph/table/list */
    private static final String BLOCK_SCHEMA = "块 JSON schema："
            + "{\"blocks\":["
            + "{\"type\":\"heading\",\"level\":2,\"text\":\"小标题\"},"
            + "{\"type\":\"paragraph\",\"text\":\"段落文本\"},"
            + "{\"type\":\"table\",\"caption\":\"（可选）\",\"headers\":[\"列1\",\"列2\"],"
            + "\"rows\":[[\"行1列1\",\"行1列2\"]]},"
            + "{\"type\":\"list\",\"ordered\":true,\"items\":[\"条目\"]}"
            + "]}";

    private static final String SYSTEM_PROMPT = "你是招投标公告正文排版助手，输出严格受控为结构化块。"
            + "输入是公告正文文本——来源可能是网页导出/扫描，原表格信息常被拍平成一行行粘连文字"
            + "（如\"第1名陕西化建工程有限责任公司1.80合同签订之日起至2027年3月31日秦成立…\"、"
            + "\"序号 候选供应商名称 中选金额（元）1 河南省宇祯建设有限公司 2,297,150.00\"）。"
            + "请把它恢复为结构化块。"
            + "默认必须输出 JSON 对象字面量：" + BLOCK_SCHEMA + "（不要 markdown 标题符号、不要 ** 加粗、"
            + "不要代码块围栏、不要 JSON 以外的解释文字）。"
            + "仅在自认为无法用上述 JSON 块表达时，允许改输出 Markdown（# 标题 + | 管道表格 | + 段落，会被自动转换）——"
            + "除此之外绝不输出 JSON 以外的格式。"
            + "规则：①\"每行一条记录\"的表格式信息（候选人/标段/金额/得分/联系方式键值等）必须还原成 table 块，"
            + "不要留在段落里粘连；②普通叙述段归 paragraph；③不得新增、改写或遗漏原文信息（不编造，可适度拆分长段）；"
            + "④联系方式等键值对可用小表格或分行段落表达，保持可读。"
            + "输出示例（仅示意结构，内容必须来自输入）："
            + "{\"blocks\":[{\"type\":\"heading\",\"level\":2,\"text\":\"一、中标候选人基本情况\"},"
            + "{\"type\":\"table\",\"headers\":[\"排序\",\"中标候选人名称\",\"投标下浮率（%）\"],"
            + "\"rows\":[[\"第1名\",\"陕西化建工程有限责任公司\",\"1.80\"]]}]}";

    /** AI 模块 REST 调用骨架（租户跟随/鉴权/超时） */
    @Autowired
    private AiHttpSupport aiHttpSupport;

    /**
     * 调用 AI 把正文文本排版为结构化块。
     *
     * @param title       公告标题（上下文）
     * @param noticeType  公告类型原文（上下文，可空）
     * @param contentText 正文文本（拍平/粘连原样传入）
     * @return 结构化块；输出非法（非 JSON / 无 blocks）时返回空表（由调用方归类失败）
     */
    public List<BodyBlock> structure(String title, String noticeType, String contentText) {
        try {
            String conversationId = aiHttpSupport.createConversation();
            String userContent = buildUserContent(title, noticeType, contentText);
            String reply = aiHttpSupport.sendMessage(conversationId, userContent);
            return parseBlocks(reply);
        } catch (IOException e) {
            throw new IllegalStateException("正文 AI 兜底排版调用失败: " + e.getMessage(), e);
        }
    }

    private String buildUserContent(String title, String noticeType, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("【公告标题】").append(title == null ? "" : title).append('\n');
        if (StringUtils.hasText(noticeType)) {
            sb.append("【公告类型】").append(noticeType).append('\n');
        }
        if (StringUtils.hasText(text)) {
            String limited = text.length() > MAX_INLINE_TEXT
                    ? text.substring(0, MAX_INLINE_TEXT) + "…[截断]"
                    : text;
            sb.append("【正文】\n").append(limited).append('\n');
        }
        return sb.toString();
    }

    /** 剥 fence 后解析 blocks 数组（type 未知/缺关键字段的块丢弃，不阻塞整体）。
     *  容错：输出非 JSON 时若为 Markdown（模型"排版"心智常见，含 md 表格）→ md→blocks 转换；仍失败返回空表。 */
    private List<BodyBlock> parseBlocks(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        List<BodyBlock> blocks = parseJsonBlocks(raw);
        if (!blocks.isEmpty()) {
            return blocks;
        }
        List<BodyBlock> markdownBlocks = MarkdownBodyBlocksParser.parse(raw);
        if (!markdownBlocks.isEmpty()) {
            log.warn("[parseBlocks][AI 输出为 Markdown（len={}），已按 md→blocks 容错转换]", raw.length());
            return markdownBlocks;
        }
        log.warn("[parseBlocks][AI 输出无法结构化，len={}] {}", raw.length(),
                raw.substring(0, Math.min(raw.length(), 200)));
        return blocks;
    }

    private List<BodyBlock> parseJsonBlocks(String raw) {
        List<BodyBlock> blocks = new ArrayList<>();
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonNode node = AiHttpSupport.MAPPER.readTree(text);
            JsonNode array = node.path("blocks");
            if (!array.isArray()) {
                return blocks;
            }
            for (JsonNode item : array) {
                BodyBlock block = toBlock(item);
                if (block != null) {
                    blocks.add(block);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return blocks;
    }

    private BodyBlock toBlock(JsonNode item) {
        String type = item.path("type").asText("");
        switch (type) {
            case "heading": {
                String text = item.path("text").asText("").trim();
                if (text.isEmpty()) {
                    return null;
                }
                int level = item.path("level").asInt(2);
                return BodyBlock.heading(Math.min(6, Math.max(1, level)), text);
            }
            case "paragraph": {
                String text = item.path("text").asText("").trim();
                return text.isEmpty() ? null : BodyBlock.paragraph(text);
            }
            case "table": {
                BodyBlock block = new BodyBlock();
                block.setType("table");
                String caption = item.path("caption").asText("").trim();
                if (!caption.isEmpty()) {
                    block.setCaption(caption);
                }
                List<String> headers = toTextList(item.path("headers"));
                block.setHeaders(headers.isEmpty() ? null : headers);
                List<List<String>> rows = new ArrayList<>();
                for (JsonNode row : item.path("rows")) {
                    List<String> cells = toTextList(row);
                    if (!cells.isEmpty()) {
                        rows.add(cells);
                    }
                }
                block.setRows(rows);
                return block;
            }
            case "list": {
                boolean ordered = item.path("ordered").asBoolean(false);
                List<String> items = toTextList(item.path("items"));
                return items.isEmpty() ? null : BodyBlock.list(ordered, items);
            }
            default:
                return null;
        }
    }

    private List<String> toTextList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(v -> list.add(v.asText("")));
        }
        return list;
    }

}
