package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// 规范依据：《外部信息获取 v1.0》§四（下载通道）/§11（频控与合规）；用户口径 2026-09-10
//   「解决现有方案中存在的缺陷（如反爬策略不足、异常处理缺失…）」。
// 业务目标：把"抓取失败"从一根 IOException 细分为可决策的三类，让重试策略有的放矢：
//   · Transient（瞬时）：网络抖动/超时/5xx/408 —— 值得退避重试（现有 attempt ≤3 语义）；
//   · AntiCrawl（反爬拦截）：403/429/Retry-After/验证码特征 —— 必须**放慢并拉长退避**，绝不立刻重试（否则等同于攻击）；
//   · Permanent（永久）：404/410/401 —— 重试无意义，快速失败并给出明确原因，不浪费重试次数与对方资源。
// 为什么继承 IOException：既有调用方（processor/runner/channel）均以 IOException 捕获，细分后**向后兼容**，
//   不需要一次性改动全部调用点，降低重构风险。

import java.io.IOException;

/**
 * 抓取异常族（见类头说明：瞬时 / 反爬 / 永久）。
 */
public class FetchException extends IOException {

    /** 建议等待时长（秒；来自 Retry-After，可能为空） */
    private final Integer retryAfterSeconds;
    /** HTTP 状态码（本地异常如超时/超限为 null） */
    private final Integer statusCode;

    public FetchException(String message, Integer statusCode, Integer retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    /** 瞬时失败：重试有意义（网络/超时/5xx） */
    public static class Transient extends FetchException {
        public Transient(String message, Integer statusCode, Integer retryAfterSeconds, Throwable cause) {
            super(message, statusCode, retryAfterSeconds, cause);
        }

        public Transient(String message, Integer statusCode, Integer retryAfterSeconds) {
            this(message, statusCode, retryAfterSeconds, null);
        }
    }

    /** 反爬拦截：403/429/Retry-After/验证码页特征——需更长的冷却，禁止紧凑重试 */
    public static class AntiCrawl extends Transient {

        public AntiCrawl(String message, Integer statusCode, Integer retryAfterSeconds) {
            super(message, statusCode, retryAfterSeconds);
        }
    }

    /** 永久失败：404/410/401 等，重试无意义 */
    public static class Permanent extends FetchException {
        public Permanent(String message, Integer statusCode) {
            super(message, statusCode, null, null);
        }
    }

}
