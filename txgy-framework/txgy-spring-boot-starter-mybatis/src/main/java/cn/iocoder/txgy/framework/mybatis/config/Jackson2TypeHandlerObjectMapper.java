package cn.iocoder.txgy.framework.mybatis.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * MyBatis-Plus Jackson 2 版 {@link JacksonTypeHandler} 的 ObjectMapper 初始化器
 *
 * <p>背景：MyBatis-Plus 的 Jackson 2 版 {@link JacksonTypeHandler} 默认使用一个未注册
 * Java 8 时间模块的 ObjectMapper，因此 DO 中形如 {@code List<Shift>}（含 LocalTime）、
 * LocalDate、LocalDateTime 的 JSON 字段在写入时会抛出：</p>
 *
 * <pre>
 * Java 8 date/time type `java.time.LocalTime` not supported by default:
 * add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling
 * </pre>
 *
 * <p>这里注册 JavaTimeModule，并关闭"日期写成时间戳数组"，让 LocalTime 按字段上的
 * {@code @JsonFormat(pattern = "HH:mm")} 序列化成 {@code "07:00"}，与历史数据格式保持一致，
 * 例如 HRM 考勤组的 {@code shifts}、特殊日期的 {@code date}。</p>
 *
 * @author OPENLAB BS
 */
public class Jackson2TypeHandlerObjectMapper {

    private static volatile boolean initialized = false;

    /**
     * 初始化 Jackson 2 版 JacksonTypeHandler 的 ObjectMapper（可重复调用，幂等）
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // 日期按 ISO 字符串写入，而不是 [2026,9,1,0,0] 形式的时间戳数组
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 容忍历史数据结构里多余的字段，避免反序列化失败
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JacksonTypeHandler.setObjectMapper(objectMapper);
        initialized = true;
    }

}
