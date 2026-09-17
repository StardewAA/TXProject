package cn.iocoder.txgy.module.wms.service.ai;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 AI 会话的上下文：既用于指代消解（“它”“那个分类”等），
 * 也用于把真实多轮对话历史回传给大模型，让模型自己理解上下文。
 */
@Data
@NoArgsConstructor
public class ChatContext {

    /** 过期时间：10 分钟 */
    @JsonIgnore
    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L;

    /** 历史消息保留的最大轮数（一问一答算一轮），防止 prompt 无限膨胀 */
    @JsonIgnore
    private static final int MAX_HISTORY_TURNS = 6;

    /** 上一条消息原文 */
    private String lastText;

    /** 上一条消息解析出的意图类型 */
    private String lastType;

    /** 上一条消息涉及的主体名称列表 */
    private List<String> lastNames;

    /** 多轮对话历史（role: user / assistant），最新的一轮在末尾 */
    private List<JSONObject> history = new ArrayList<>();

    /** 创建时间戳 */
    private long createTime = System.currentTimeMillis();

    public static ChatContext of(String lastText, String replyText, String lastType, List<String> lastNames) {
        ChatContext ctx = new ChatContext();
        ctx.lastText = lastText;
        ctx.lastType = lastType;
        ctx.lastNames = lastNames;
        return ctx;
    }

    /**
     * 把一轮对话追加到历史中，并裁剪到最大保留轮数。
     *
     * @param userText      用户这一轮的提问
     * @param assistantText 助手这一轮的回复
     */
    public void appendTurn(String userText, String assistantText) {
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(message("user", userText));
        history.add(message("assistant", assistantText));
        int maxMessages = MAX_HISTORY_TURNS * 2;
        if (history.size() > maxMessages) {
            history = new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
        }
    }

    /**
     * 取历史消息（不含本轮 user），用于拼装 calls 的 messages。
     */
    public List<JSONObject> historyMessages() {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return history;
    }

    private static JSONObject message(String role, String content) {
        JSONObject msg = new JSONObject(true);
        msg.set("role", role);
        msg.set("content", content);
        return msg;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createTime > EXPIRE_MILLIS;
    }

}
