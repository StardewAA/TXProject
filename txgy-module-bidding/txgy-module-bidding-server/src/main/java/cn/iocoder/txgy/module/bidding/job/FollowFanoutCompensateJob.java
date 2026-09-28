package cn.iocoder.txgy.module.bidding.job;

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 关注扇出补偿扫描（设计 v0.6 §7.6「补偿扫描，已规划未实现」→ 本轮落地）。
 *
 * <p>[为什么需要] 状态变更此前只有"应用审核接口"一条触发路径。凡绕过该接口的变更
 * —— 批量导入、DBA 改库、数据迁移、刷数脚本 —— 都不会补扇出。
 * 实测证据（契约《关注扇出重放补偿与维度回填-2026-09-29》）：2026-09-14 同一秒有 1055 条公告
 * 被批量 UPDATE 为 ACTIVE（creator/updater 均为 NULL），扇出从未触发，
 * 该批订阅一条提醒都没有，且**此前无从发现**（没有任何扫描在查这件事）。
 *
 * <p>且 {@code approveExternal} 里扇出失败**只记日志**（代码注释自认
 * "补偿扫描未实现，需人工复核重放"），一次失败 = 该提醒永久丢失。
 *
 * <p>[为什么可以无脑重跑] 事件键含 contentHash
 * （用户-关注项-来源-公告id-contentHash），重放同一公告不产生重复提醒，
 * 仅"内容确实变过"才新建事件 —— 幂等是本任务成立的唯一前提，见 {@link FollowService#refanout}。
 *
 * <p>[扫什么] 近 N 天内转 ACTIVE、且有维度键（project/tender/section）的公告，
 * 逐条调 refanout。不扫全表：早期公告早已扇出过，重扫只是空转；
 * 窗口取 7 天覆盖"周末故障 + 周一发现"的典型恢复周期。
 *
 * <p>[默认关] {@code bidding.follow-fanout.compensate.enabled:false} ——
 * 与 {@link CredentialAlertJob} 同理：① 本模块 @Scheduled 是进程内调度，多实例会各扫一遍
 * （幂等所以只会浪费资源，但仍应由运维确认执行方式）；② 会把历史漏扇出一次性补齐，
 * 属可见的业务动作（用户会收到积压的提醒）。上线动作 = 运维改一行配置。
 *
 * <p>[失败留痕] 契约 §二.3 要求"扇出失败要留痕"。本任务逐条 try/catch 并**逐条记日志**
 * （含 noticeId 与异常），日志级别 ERROR 便于接告警；单条失败不中断整批。
 * （未新建"待重放表"：现有 {@code refanout} 接口 + dryRun 已可人工复核，
 * 加表属额外建模，待确认真有持续失败再补——避免为一次性需求造表。）
 */
@Slf4j
@Component
public class FollowFanoutCompensateJob {

    @Resource
    private NoticeExternalMapper noticeExternalMapper;

    @Resource
    private FollowService followService;

    /** 调度总开关（默认关：上线需运维确认后开启，理由见类注释）。 */
    @Value("${bidding.follow-fanout.compensate.enabled:false}")
    private boolean enabled;

    /** 扫描窗口（天）：只看近 N 天转 ACTIVE 的公告（早期公告早已扇出，重扫是空转）。 */
    @Value("${bidding.follow-fanout.compensate.window-days:7}")
    private int windowDays;

    /** 单次扫描的公告数上限（积压时避免单次跑太久占住调度线程）。 */
    @Value("${bidding.follow-fanout.compensate.batch-size:500}")
    private int batchSize;

    /**
     * 每 30 分钟补扫一次（cron 可配）。
     *
     * <p>为什么比预警任务频繁：扇出是"用户等着的提醒"，延迟直接体现为"关注了没反应"；
     * 而补偿本身幂等且只查近 7 天，开销可控。
     */
    @Scheduled(cron = "${bidding.follow-fanout.compensate.cron:0 */30 * * * ?}")
    public void compensateFanoutJob() {
        if (!enabled) {
            return;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(Math.max(windowDays, 1));
            List<NoticeExternalDO> notices = noticeExternalMapper.selectList(
                    new LambdaQueryWrapper<NoticeExternalDO>()
                            .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus())
                            .ge(NoticeExternalDO::getPublishTime, since)
                            // 只扫"有维度键"的公告：无键的公告匹配不到任何维度订阅（公告级订阅走 targetKey=id，不受影响）
                            .and(w -> w.isNotNull(NoticeExternalDO::getProjectCode)
                                    .or().isNotNull(NoticeExternalDO::getTenderProjectCode)
                                    .or().isNotNull(NoticeExternalDO::getBidSectionCodes))
                            .orderByDesc(NoticeExternalDO::getPublishTime)
                            .last("LIMIT " + Math.max(batchSize, 1)));
            if (notices == null || notices.isEmpty()) {
                return;
            }
            int totalCreated = 0;
            int failed = 0;
            for (NoticeExternalDO notice : notices) {
                try {
                    var result = followService.refanout(notice.getId(), false);
                    totalCreated += result.getCreated() == null ? 0 : result.getCreated();
                } catch (Exception e) {
                    failed++;
                    // 契约 §二.3：失败必须留痕（此前只 log 在 approve 那一处，扫描侧完全不可见）
                    log.error("[follow-fanout-compensate] 单条重放失败 noticeId={} title={}",
                            notice.getId(), notice.getTitle(), e);
                }
            }
            if (totalCreated > 0 || failed > 0) {
                log.info("[follow-fanout-compensate] 扫描 {} 条（近 {} 天），补发 {} 条动态，失败 {} 条",
                        notices.size(), windowDays, totalCreated, failed);
            }
        } catch (Exception e) {
            // 异常不逃逸：否则会被调度框架按策略跳过后续执行（提醒长期静默）
            log.error("[follow-fanout-compensate] 补偿扫描执行失败", e);
        }
    }
}
