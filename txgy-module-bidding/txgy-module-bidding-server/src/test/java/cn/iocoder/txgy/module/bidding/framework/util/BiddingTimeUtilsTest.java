package cn.iocoder.txgy.module.bidding.framework.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 时间口径唯一转换点测试（2026-09-13 统一）。
 *
 * <p>钉住三件事：① null 进 null 出（出参与入库都靠这个兜底，避免 NPE 或 0 值污染）；
 * ② 往返一致（毫秒 ↔ LocalDateTime 同一种时区，不因夏令时/容器时区漂移）；③ ES ISO 格式与外部公告索引现行口径一致。
 */
class BiddingTimeUtilsTest {

    @Test
    void toEpochMilli_nullSafe() {
        assertNull(BiddingTimeUtils.toEpochMilli(null));
        assertNull(BiddingTimeUtils.fromEpochMilli(null));
        assertNull(BiddingTimeUtils.toEsDateTime(null));
        assertNull(BiddingTimeUtils.fromEsDateTime(null));
        assertNull(BiddingTimeUtils.fromEsDateTime("  "));
    }

    @Test
    void epochRoundTrip_isStable() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 13, 10, 30, 45);
        Long millis = BiddingTimeUtils.toEpochMilli(time);
        assertEquals(time, BiddingTimeUtils.fromEpochMilli(millis), "同一时区下往返必须一致");
    }

    @Test
    void esDateTime_matchesExternalIndexFormat() {
        // bidding-notice-external 索引现行口径：yyyy-MM-dd'T'HH:mm:ss（秒精度，无时区后缀）
        assertEquals("2026-09-13T10:30:45",
                BiddingTimeUtils.toEsDateTime(LocalDateTime.of(2026, 9, 13, 10, 30, 45)));
        assertEquals(LocalDateTime.of(2026, 9, 13, 10, 30, 45),
                BiddingTimeUtils.fromEsDateTime("2026-09-13T10:30:45"));
    }
}
