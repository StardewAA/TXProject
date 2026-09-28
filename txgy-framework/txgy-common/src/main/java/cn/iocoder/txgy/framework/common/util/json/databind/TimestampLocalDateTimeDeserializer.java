package cn.iocoder.txgy.framework.common.util.json.databind;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 基于时间戳的 LocalDateTime 反序列化器
 *
 * 同时支持两种格式：
 * 1. epoch 毫秒数字（JSON number）— 默认行为，向后兼容
 * 2. ISO 字符串（JSON string）— 兼容外部系统传入的 "2026-09-14T15:09:08" 格式
 *
 * @author 老五
 */
public class TimestampLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    public static final TimestampLocalDateTimeDeserializer INSTANCE = new TimestampLocalDateTimeDeserializer();

    private static final DateTimeFormatter CUSTOM_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        // 情况一：字符串类型
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            String text = p.getValueAsString();
            if (text != null && !text.isEmpty()) {
                // 1a. 纯数字字符串 → epoch 毫秒（前端 dayjs.valueOf() 经 JSON 序列化后变字符串）
                if (text.matches("\\d{10,16}")) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(text)), ZoneId.systemDefault());
                }
                try {
                    // 1b. ISO 本地时间："2026-09-14T15:09:08"
                    return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception e1) {
                    try {
                        // 1c. 空格分隔格式："2026-09-14 16:05:55"（Ant Design Vue 默认日期格式）
                        return LocalDateTime.parse(text, CUSTOM_FORMATTER);
                    } catch (Exception e2) {
                        try {
                            // 1d. ISO 带时区："2026-09-14T15:09:08Z" / "2026-09-14T15:09:08+08:00"
                            return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
                        } catch (Exception e3) {
                            throw new IllegalArgumentException("无法解析时间字符串: " + text, e3);
                        }
                    }
                }
            }
        }
        // 情况二：数字类型，按 epoch 毫秒处理（默认行为，向后兼容）
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getValueAsLong()), ZoneId.systemDefault());
    }

}
