package cn.iocoder.txgy.module.erp.util;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 经营统计（销售 / 采购）的时间分桶工具
 *
 * <p>供首页「统计图表」按不同时间维度取数使用，支持维度：</p>
 * <ul>
 *     <li>{@link #RANGE_6M}：近 6 个月，按月（默认，与历史行为一致）</li>
 *     <li>{@link #RANGE_7D}：近 7 天，按天</li>
 *     <li>{@link #RANGE_30D}：近 30 天，按天</li>
 *     <li>{@link #RANGE_YEAR}：今年 1 月至当前月，按月</li>
 *     <li>{@link #RANGE_ALL}：累计，按月（最多回溯 {@link #ALL_MAX_MONTHS} 个月，避免画出无意义的点）</li>
 * </ul>
 *
 * <p>时间区间统一为<b>左闭右开</b> {@code [startTime, endTime)}，与统计服务
 * {@code getXxxPrice(start, end)} 的口径一致。</p>
 *
 * @author OPENLAB BS
 */
public class ErpStatisticsTimeUtils {

    public static final String RANGE_6M = "6m";
    public static final String RANGE_7D = "7d";
    public static final String RANGE_30D = "30d";
    public static final String RANGE_YEAR = "year";
    public static final String RANGE_ALL = "all";

    /** 「累计」维度最多回溯的月份数 */
    private static final int ALL_MAX_MONTHS = 24;

    /** 按天展示时的标签格式 */
    private static final String DAY_PATTERN = "MM-dd";

    /**
     * 一个时间桶：图表上的一个点
     *
     * @param label     横轴标签（按月 yyyy-MM，按天 MM-dd）
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     */
    public record Bucket(String label, LocalDateTime startTime, LocalDateTime endTime) {
    }

    /**
     * 根据维度构建时间桶列表
     *
     * @param range      维度，见本类常量；为空时按 {@link #RANGE_6M}
     * @param monthCount {@link #RANGE_6M} 维度下的月份数量
     */
    public static List<Bucket> build(String range, int monthCount) {
        LocalDateTime now = LocalDateTime.now();
        String normalized = range == null ? RANGE_6M : range;
        return switch (normalized) {
            case RANGE_7D -> buildDays(now, 7);
            case RANGE_30D -> buildDays(now, 30);
            case RANGE_YEAR -> buildMonths(now, Math.max(1, now.getMonthValue()));
            case RANGE_ALL -> buildMonths(now, ALL_MAX_MONTHS);
            default -> buildMonths(now, Math.max(1, monthCount));
        };
    }

    private static List<Bucket> buildDays(LocalDateTime now, int days) {
        List<Bucket> buckets = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime start = LocalDateTimeUtil.beginOfDay(now.minusDays(i));
            buckets.add(new Bucket(LocalDateTimeUtil.format(start, DAY_PATTERN), start, start.plusDays(1)));
        }
        return buckets;
    }

    private static List<Bucket> buildMonths(LocalDateTime now, int months) {
        List<Bucket> buckets = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime start = LocalDateTimeUtils.beginOfMonth(now.minusMonths(i));
            buckets.add(new Bucket(LocalDateTimeUtil.format(start, DatePattern.NORM_MONTH_PATTERN),
                    start, start.plusMonths(1)));
        }
        return buckets;
    }

}
