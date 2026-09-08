package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;
// 规范依据：设计文档 v0.6 §7（AI 解析能力由 AI 模块承载）；接入形态=AI 模块 chat 会话 REST（本地 ai-server mock-enable 免鉴权联调）

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模块会话抽取实现（配置 {@code bidding.ai.enabled=true} 时生效）。
 *
 * <p>调用 AI 模块对外 REST（admin-api 前缀，与前端同一入口形态）：
 * <ol>
 *   <li>POST {base}/ai/chat/conversation/create-my（{} 默认会话，返回 conversationId；可用 bidding.ai.conversation-id 固定复用）；</li>
 *   <li>POST {base}/ai/chat/message/send：系统提示词要求只输出 JSON；正文文本直接内联（PDF 文字层），
 *       扫描件（无文本）走 attachmentUrls 传 PDF 公网 URL 由多模态模型转写；</li>
 *   <li>解析 receive.content：剥 markdown code fence 后按 JSON 解析。</li>
 * </ol>
 * 鉴权/租户跟随/超时统一由 {@link AiHttpSupport} 承担（enabled=true 时注入）。
 * 输出 schema 见 {@link #SYSTEM_PROMPT}，字段约定与 docs 02 展示约定一致。
 */
@Slf4j
@Component("aiChatNoticeExtractor")
@ConditionalOnProperty(prefix = "bidding.ai", name = "enabled", havingValue = "true")
public class AiChatNoticeExtractor implements AiNoticeExtractor {

    /** 固定复用会话 id（可选；空则每次 create-my） */
    @Value("${bidding.ai.conversation-id:}")
    private String conversationId;

    /** 正文内联上限（防超大文本撑爆请求体） */
    private static final int MAX_INLINE_TEXT = 60_000;

    /** AI 模块 REST 调用骨架（base-url/租户跟随/鉴权/超时，见 {@link AiHttpSupport}） */
    @Autowired
    private AiHttpSupport aiHttpSupport;

    /** 行业判定口径（管道防腐保温行业）——用户口径 2026-09-10：提示词必须显式定义"什么是管道防腐保温行业"。
     *  界定：本行业指 金属管道/储罐/装置/设备（含其附属设施：支架、法兰、阀门、管件）在石化、化工、
     *  电力、热力、燃气、给排水等工业场景下的 防腐蚀（涂装/衬里/阴极保护）与 保温/保冷/隔热（绝热层
     *  施工/维修/材料供应/检测）工程。明确纳入：管道防腐、管道保温、储罐防腐、绝热工程、防腐保温材料、
     *  阴极保护、涂装防腐。明确排除：建筑外墙保温/屋面保温、通用装修、非管道类设备制造（不含保温工序）、
     *  单纯货物买卖（非防腐保温材料）。 */
    private static final String INDUSTRY_RULE = "行业判定口径（管道防腐保温行业）定义："
            + "本行业指金属管道/储罐/装置/设备及其附属设施（支架、法兰、阀门、管件）在工业场景（石化、化工、电力、热力、燃气、给排水等）"
            + "下的防腐蚀工程（涂装防腐、衬里、阴极保护）与保温/保冷/隔热绝热工程（绝热层施工、维修、材料供应、检测验收）。"
            + "→ 招标内容涉及【管道防腐、管道保温、储罐防腐、绝热工程、防腐保温材料、阴极保护、涂装防腐】等即 industryFlag=BELONGS；"
            + "涉及【建筑外墙保温/屋面保温、通用装修、与管道无关的设备制造、非防腐保温材料的单纯货物买卖】等明确无关 → industryFlag=NOT_BELONGS；"
            + "信息不足无法判断 → industryFlag=UNCERTAIN。";

    /** 系统提示词（2026-09-10 用户口径修订）：AI 只校验/输出结构化字段，不取正文——
     *  不要求转写全文、不回传正文（正文由下载链路 FileApi 源文件保存，勿让模型复制正文）。 */
    private static final String SYSTEM_PROMPT = "你是招投标公告信息抽取与行业判定助手。"
            + "输入给你【公告类型/标题/正文】，你只抽取并输出一个 JSON 对象（不要 markdown 代码块、不要任何解释文字、"
            + "不要把正文原文复制回传），字段为："
            + "{\"title\":\"公告标题\",\"projectName\":\"项目名称\",\"tenderNo\":\"招标/采购编号\","
            + "\"budgetAmount\":\"预算金额（纯数字，元）\",\"bidOpenTime\":\"开标/递交截止时间\","
            + "\"issuer\":\"招标人/采购人\",\"contactPerson\":\"联系人\",\"contactPhone\":\"联系电话\","
            + "\"qualificationSummary\":\"资格要求摘要\","
            + "\"noticeFullText\":\"恒为空串（本模块不采集正文转写，正文由源文件保存）\","
            + "\"industryFlag\":\"BELONGS/NOT_BELONGS/UNCERTAIN 三选一\"," + "\"industryConfidence\":0~1 的判定置信度数字,"
            + "\"industryReason\":\"判定理由一句话\","
            + "\"industryHitKeywords\":[\"命中的行业关键词数组，未命中给空数组\"]}。"
            + INDUSTRY_RULE
            + "正文里没有的字段给 null 或空串，不要编造。";

    @Override
    public String name() {
        return "ai-chat";
    }

    @Override
    public AiNoticeExtractResult extract(AiNoticeExtractRequest request) {
        try {
            String conv = resolveConversationId();
            String userContent = buildUserContent(request);
            String reply = aiHttpSupport.sendMessage(conv, userContent);
            Map<String, Object> fields = parseJsonObject(reply);
            Object fullText = fields.remove("noticeFullText");
            return new AiNoticeExtractResult(fields, fullText instanceof String s && StringUtils.hasText(s) ? s : null);
        } catch (IOException e) {
            throw new IllegalStateException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    /** 固定会话或自动创建（每次 create-my 会沉淀冗余会话，可配置 conversation-id 复用） */
    private String resolveConversationId() throws IOException {
        if (StringUtils.hasText(conversationId)) {
            return conversationId.trim();
        }
        return aiHttpSupport.createConversation();
    }

    private String buildUserContent(AiNoticeExtractRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("【公告类型】").append(nullToEmpty(req.getNoticeTypeRaw())).append('\n');
        sb.append("【标题】").append(nullToEmpty(req.getTitle())).append('\n');
        if (StringUtils.hasText(req.getPdfUrl())) {
            sb.append("【PDF 附件】").append(req.getPdfUrl())
                    .append("（扫描件无文字层，请阅读附件后转写全文并抽取字段）").append('\n');
        }
        String text = req.getContentText();
        if (StringUtils.hasText(text)) {
            if (text.length() > MAX_INLINE_TEXT) {
                text = text.substring(0, MAX_INLINE_TEXT) + "…[截断]";
            }
            sb.append("【正文】\n").append(text).append('\n');
        }
        return sb.toString();
    }

    /** 剥 markdown code fence 后解析 JSON 对象（模型偶发包 ```json） */
    private Map<String, Object> parseJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonNode node = AiHttpSupport.MAPPER.readTree(text);
            if (!node.isObject()) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), toPlain(e.getValue())));
            return map;
        } catch (IOException e) {
            log.warn("[parseJsonObject][AI 输出非 JSON，len={}] {}", raw == null ? 0 : raw.length(),
                    raw == null ? "" : raw.substring(0, Math.min(raw.length(), 200)));
            return new LinkedHashMap<>();
        }
    }

    private Object toPlain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.asLong() : node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray() || node.isObject()) {
            return node.toString();
        }
        return node.asText();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}
