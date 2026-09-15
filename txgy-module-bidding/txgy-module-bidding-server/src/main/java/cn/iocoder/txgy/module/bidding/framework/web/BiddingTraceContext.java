package cn.iocoder.txgy.module.bidding.framework.web;

import org.slf4j.MDC;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 电子证照模块的链路追踪上下文（契约 §0.4 的 traceId 来源）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§0.3/§0.4（响应头 X-Trace-Id + 错误体 traceId）。
 * <p>为什么需要它：报障时若只有"某某接口报错了"，排查要在海量日志里按时间捞；
 * 带上 traceId 后可一次定位到具体请求（含上游传值，便于跨服务串联）。
 * <p>业务目标：每一次失败请求都可被唯一定位，用户与运维用同一个 ID 对话。
 */
public final class BiddingTraceContext {

    /** MDC 键名（日志模板可直接引用 %X{traceId}）。 */
    public static final String MDC_KEY = "traceId";
    /** 请求/响应头名称。 */
    public static final String HEADER = "X-Trace-Id";

    private BiddingTraceContext() {
    }

    /** 取当前 traceId；不存在时返回 null（不自动生成，生成职责在过滤器）。 */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** 生成一个新的 traceId（16 位十六进制，足够单机高并发下不碰撞且短到可人工抄写）。 */
    public static String generate() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong())
                + Long.toHexString(ThreadLocalRandom.current().nextLong()).substring(0, 4);
    }
}
