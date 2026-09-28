package cn.iocoder.txgy.module.bidding.framework.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间口径唯一转换点（2026-09-13 统一）。
 *
 * <p><b>为什么要有这个类</b>：盘点发现本模块同一件事有四种时间表达，散落在各域各写各的转换：
 * <ol>
 *   <li>出参 {@code LocalDateTime}（平台序列化为 {@code yyyy-MM-dd HH:mm:ss}）——多数 VO（48 处）；</li>
 *   <li>出参 {@code Long} epoch 毫秒——项目/追溯/关注域（{@code ProjectServiceImpl.epoch} 手写、{@code NoticeServiceImpl}
 *       另手写一份给 ES 文档用）；</li>
 *   <li>ES {@code bidding-notice} 的 {@code publishTime} = epoch 毫秒（"便于排序"）；</li>
 *   <li>ES {@code bidding-notice-external} 的 {@code publishTime} = ISO 字符串 {@code yyyy-MM-dd'T'HH:mm:ss}。</li>
 * </ol>
 * 同一字段名两种存储类型、同一转换两处手写，导致改一处漏一处、时区口径也各写各的。本类把
 * <b>时区、格式、null 兜底</b>三件事收成一处；业务侧只调用这里，不再手写 {@code atZone/toEpochMilli}。
 *
 * <p><b>时区口径</b>：统一用 JVM 默认时区（{@link ZoneId#systemDefault()}）——与既有实现完全一致（本次不改行为，
 * 只收敛实现）。生产部署必须以 {@code -Duser.timezone=Asia/Shanghai} 固定，否则容器时区漂移会让
 * "同一时刻"在不同实例上换算成不同毫秒/字符串；这一条已写入规范时间章节。
 *
 * <p><b>尚未统一（需前端与索引排期，见 docs/模块设计与规范/时间口径盘点-2026-09-13.md）</b>：
 * 出参 Long/epoch 与 LocalDateTime 并存、两个 ES 索引的 {@code publishTime} 类型不同——
 * 这两项改动会破坏前端契约与检索排序，不能由后端单方面切换。
 */
public final class BiddingTimeUtils {

    private BiddingTimeUtils() {
    }

    /** 全模块统一时区（JVM 默认；部署须以 -Duser.timezone 固定，见类注释） */
    public static final ZoneId ZONE = ZoneId.systemDefault();

    /** ES 索引里的日期时间格式（ISO-8601，秒精度；{@code bidding-notice-external} 现行口径） */
    public static final DateTimeFormatter ES_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** {@code LocalDateTime → epoch 毫秒}；null 进 null 出（出参/ES 文档用） */
    public static Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    /** {@code epoch 毫秒 → LocalDateTime}；null 进 null 出（入参/回读用） */
    public static LocalDateTime fromEpochMilli(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
    }

    /** {@code LocalDateTime → ES ISO 字符串}；null 进 null 出 */
    public static String toEsDateTime(LocalDateTime time) {
        return time == null ? null : ES_DATE_TIME.format(time.atZone(ZONE));
    }

    /** {@code ES ISO 字符串 → LocalDateTime}（检索回读用）；空/null 进 null 出 */
    public static LocalDateTime fromEsDateTime(String text) {
        return text == null || text.isBlank() ? null : LocalDateTime.parse(text, ES_DATE_TIME);
    }

}
