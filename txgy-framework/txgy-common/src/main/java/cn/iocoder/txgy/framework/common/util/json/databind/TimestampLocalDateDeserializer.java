package cn.iocoder.txgy.framework.common.util.json.databind;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 兼容时间戳的 LocalDate 反序列化器（QA 修复 2026-09-23）
 *
 * <p>背景：接口层的日期口径不统一——LocalDateTime 只接受 epoch 毫秒，而 LocalDate 走 Jackson 默认
 * 的 ISO 解析只接受 {@code "yyyy-MM-dd"} 字符串。前端/第三方按统一口径传时间戳时，
 * {@code LocalDate} 字段（如招聘需求单的「计划进场日期」、Offer 的「计划入职日期」）会直接 500。</p>
 *
 * <p>本反序列化器同时接受：</p>
 * <ol>
 *   <li>JSON number：epoch 毫秒（按系统时区换算为日期）</li>
 *   <li>纯数字字符串：epoch 毫秒（前端 dayjs.valueOf() 序列化后可能变字符串）</li>
 *   <li>日期字符串：{@code "yyyy-MM-dd"} / ISO 日期时间（取其日期部分）</li>
 * </ol>
 */
public class TimestampLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    public static final TimestampLocalDateDeserializer INSTANCE = new TimestampLocalDateDeserializer();

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        // 一、数字：epoch 毫秒
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT || p.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return LocalDate.ofInstant(Instant.ofEpochMilli(p.getValueAsLong()), ZoneId.systemDefault());
        }
        // 二、字符串：数字字符串按 epoch 毫秒，其余按日期/日期时间解析
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            String text = p.getValueAsString();
            if (text != null && !text.isBlank()) {
                String trimmed = text.trim();
                if (trimmed.matches("\\d{10,16}")) {
                    return LocalDate.ofInstant(Instant.ofEpochMilli(Long.parseLong(trimmed)),
                            ZoneId.systemDefault());
                }
                try {
                    return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ignored) {
                    try {
                        // 兼容 "2026-10-08T00:00:00" / "2026-10-08 00:00:00"
                        return java.time.LocalDateTime.parse(trimmed.replace(' ', 'T')).toLocalDate();
                    } catch (Exception e2) {
                        throw new IllegalArgumentException("无法解析日期字符串: " + text, e2);
                    }
                }
            }
            return null;
        }
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        return (LocalDate) ctxt.handleUnexpectedToken(LocalDate.class, p);
    }

}
