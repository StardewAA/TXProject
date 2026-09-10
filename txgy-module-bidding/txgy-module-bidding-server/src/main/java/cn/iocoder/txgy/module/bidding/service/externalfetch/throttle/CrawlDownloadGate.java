package cn.iocoder.txgy.module.bidding.service.externalfetch.throttle;

// 规范依据：《外部信息获取 v1.0》§六（消费端执行）/§11（频控与会话断点）；用户口径 2026-09-10：
//   「下载器的时间间隔（Sleep/Delay）必须且只能作用于『下载公告原文文件』这一动作」
//   「每次下载原文文件之前，必须强制执行休眠等待，严禁无间隔高频请求」
//   「排队中的任务绝对不允许直接触发下载」。
// 业务目标：把"对目标站点发起原文下载"的速率限制收敛到**唯一闸门**，并保证：
//   ① 进程重启不重置（原 lastConsumeAtMs 归零 → 重启后立刻下载）；
//   ② 多实例部署下全局仍是"每 interval 一次"（原实现各实例各计时 → 速率 ×N）；
//   ③ 纯本地处理（列表已带全文，无网络下载）不占位，间隔只花在真正的下载动作上。
// 为什么放在最靠近 HTTP 调用的位置调用：间隔作用于"下载原文文件"这一动作本身，
//   而不是"任务被调度"这件事——放错位置就会出现"任务流转被限速、而下载本身不受控"的假合规。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlDownloadGateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlDownloadGateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 原文下载限速闸（合规频控的唯一入口）。
 *
 * <p>用法约定（务必遵守，否则限速形同虚设）：
 * <pre>
 *   // 异步消费端：未到间隔必须"不下载"，由调用方把任务放回 PENDING 排队
 *   if (!gate.tryAcquire(sourceCode, taskId, url)) {
 *       throw new CrawlDownloadNotDueException(gate.nextDueAt(sourceCode), ...);
 *   }
 *   // 同步直抓/批量修复（非消费端线程）：阻塞等待到位后再下载
 *   gate.acquireBlocking(sourceCode, taskId, url);
 * </pre>
 */
@Slf4j
@Component
public class CrawlDownloadGate {

    /** 全局单闸作用域（保留按源扩展：scope_key 已建唯一键） */
    private static final String SCOPE_GLOBAL = "GLOBAL";
    /** 全局闸位固定主键（幂等初始化用） */
    private static final long GATE_ID = 1L;
    /** 合规下限（秒）：规范要求源 fetch_interval_seconds ≥120，配置被误改小时以此兜底，防止"限速被关掉" */
    private static final long MIN_INTERVAL_SECONDS = 120L;
    /** 阻塞等待时的轮询间隔（毫秒） */
    private static final long POLL_MS = 1_000L;
    /** 阻塞等待的进度日志间隔（毫秒） */
    private static final long LOG_EVERY_MS = 60_000L;

    /** 下载器每条任务处理间隔（毫秒）：application.yaml bidding.crawl.runner.consumer-delay-ms（默认 5 分钟） */
    @Value("${bidding.crawl.runner.consumer-delay-ms:300000}")
    private long defaultIntervalMs;

    @Resource
    private CrawlDownloadGateMapper gateMapper;
    @Resource
    private CrawlSourceMapper crawlSourceMapper;

    /**
     * 尝试获得"下载原文文件"的资格（非阻塞）。
     *
     * @return true=已占位，可以立刻下载；false=未到间隔，<b>不得发起下载</b>
     * @throws IllegalStateException 闸位表缺失/不可用（合规闸门不可用时不放行下载：宁可停，不可违规）
     */
    public boolean tryAcquire(String sourceCode, Long taskId, String url) {
        int intervalSeconds = intervalSecondsOf(sourceCode);
        try {
            if (gateMapper.claim(SCOPE_GLOBAL, intervalSeconds, taskId, auditUrl(url)) > 0) {
                log.info("[download-gate] 放行下载 taskId={} source={} 间隔={}s url={}", taskId, sourceCode, intervalSeconds, auditUrl(url));
                return true;
            }
            // 闸位行缺失（首次部署未执行初始化/被误删）→ 幂等补建后重试一次；仍失败说明确实未到间隔
            if (gateMapper.insertIgnore(GATE_ID, SCOPE_GLOBAL) > 0) {
                return gateMapper.claim(SCOPE_GLOBAL, intervalSeconds, taskId, auditUrl(url)) > 0;
            }
            return false;
        } catch (Exception e) {
            // 失败关闭（fail-closed）：闸门不可用时拒绝下载，避免"限速失效但业务照跑"的静默违规
            throw new IllegalStateException("下载限速闸不可用（请确认已执行 sql/history/2026-09-10-crawl-download-gate.sql）: " + e.getMessage(), e);
        }
    }

    /**
     * 阻塞等待到"下载原文文件"的合规时点（同步直抓/批量回填等非消费端线程使用）。
     *
     * <p>为什么允许阻塞：这些链路本就不追求吞吐（用户口径「慢是要求，不是缺陷」），
     * 与其无间隔连发请求，不如就地等待；等待期间每 60s 打一条进度日志便于运维观察。
     */
    public void acquireBlocking(String sourceCode, Long taskId, String url) {
        long waitedMs = 0L;
        long lastLogMs = 0L;
        while (!tryAcquire(sourceCode, taskId, url)) {
            LocalDateTime dueAt = nextDueAt(sourceCode);
            long remainMs = dueAt == null ? POLL_MS : Math.max(POLL_MS, Duration.between(LocalDateTime.now(), dueAt).toMillis());
            if (waitedMs - lastLogMs >= LOG_EVERY_MS) {
                log.info("[download-gate] 等待合规下载间隔：taskId={} source={} 已等待 {}s，剩余约 {}s（{} 后可下载）",
                        taskId, sourceCode, waitedMs / 1000, remainMs / 1000, dueAt);
                lastLogMs = waitedMs;
            }
            try {
                Thread.sleep(Math.min(POLL_MS, remainMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断标记：调用方（批次任务）据此终止本轮
                throw new IllegalStateException("等待下载间隔被中断（本轮不下载，避免违规）", e);
            }
            waitedMs += Math.min(POLL_MS, remainMs);
        }
    }

    /** 下次可下载时刻（null=从未下载，可立即下载）；供任务回 PENDING 时填 next_retry_time 排队 */
    public LocalDateTime nextDueAt(String sourceCode) {
        CrawlDownloadGateDO row = gateMapper.selectOne(new LambdaQueryWrapper<CrawlDownloadGateDO>()
                .eq(CrawlDownloadGateDO::getScopeKey, SCOPE_GLOBAL).last("LIMIT 1"));
        if (row == null || row.getLastDownloadAt() == null) {
            return null;
        }
        return row.getLastDownloadAt().plusSeconds(intervalSecondsOf(sourceCode));
    }

    /** 当前生效间隔（秒）：取「全局 consumer-delay-ms」与「源 fetch_interval_seconds」的较大者，并不低于合规下限 120s */
    public int intervalSecondsOf(String sourceCode) {
        long intervalMs = Math.max(defaultIntervalMs, MIN_INTERVAL_SECONDS * 1000);
        if (StringUtils.hasText(sourceCode)) {
            try {
                CrawlSourceDO source = crawlSourceMapper.selectOne(new LambdaQueryWrapper<CrawlSourceDO>()
                        .eq(CrawlSourceDO::getSourceCode, sourceCode).last("LIMIT 1"));
                if (source != null && source.getFetchIntervalSeconds() != null && source.getFetchIntervalSeconds() > 0) {
                    intervalMs = Math.max(intervalMs, source.getFetchIntervalSeconds() * 1000L);
                }
            } catch (Exception e) {
                // 读源配置失败只影响"是否取更严格值"，按全局值继续（不放宽下限）
                log.warn("[download-gate] 读取源 {} 的 fetch_interval_seconds 失败，按全局间隔 {}s 执行: {}",
                        sourceCode, intervalMs / 1000, e.getMessage());
            }
        }
        return (int) Math.max(MIN_INTERVAL_SECONDS, intervalMs / 1000);
    }

    /** 审计列截断（last_url 列 1024；超长截断避免写失败影响下载判定） */
    private static String auditUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.length() <= 1000 ? url : url.substring(0, 1000);
    }

}
