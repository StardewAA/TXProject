package cn.iocoder.txgy.module.bidding.job;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.dal.dataobject.flow.FlowTimerDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.flow.FlowTimerMapper;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowTimerStatusEnum;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionReqVO;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTransitionService;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTransitionServiceImpl;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 招标全流程时间驱动调度器：到点触发时间轴事件（BIDDING_STARTED/BIDDING_ENDED）。
 *
 * <p>为什么不是「轮询全表的定时任务」：投标起止时间是用户配置、精确到秒（如 2026-09-30 09:00:00），
 * 朴素轮询（每分钟扫一遍所有标段）既做不到秒级、又浪费扫描。本实现改为「保存即注册」：
 * 1) save 标段/招标项目时，由 FlowTimerService 把 trigger_time（用户配置，精确到秒）写入 bidding_flow_timer（G 轨）；
 * 2) 本调度器固定延迟（默认 1 秒）只扫「到期且 WAITING」的轻量索引行（idx_trigger(status, trigger_time)），
 *    逐条执行 transitionByTimer（走状态机校验 + 留痕），成功→DONE，状态已变化→SKIPPED（幂等，无副作用）。
 *
 * <p>开关：bidding.flow.scheduler.enabled（默认 true）。多实例部署时每实例各扫一遍，因 DONE/SKIPPED 幂等，
 * 重跑只是浪费资源不会产生重复流转（与 CredentialAlertJob 同一理由，见其类注释）。
 *
 * <p>租户：bidding_flow_timer 为 G 轨（后台线程无请求租户上下文）；执行流转前手动设置租户上下文（当前单租户环境租户 1），
 * 使 transition 内部对 T 轨实体的查询/留痕正常落库，执行后清理。
 *
 * <p><b>退避（2026-09-22 故障修复）</b>：「状态未就绪」的 timer 要保留 WAITING 等下轮，
 * 但"下轮"若就是 1 秒后，而这些 timer 的前置状态**永远不会到达**（典型：草稿公告注册了
 * BIDDING_STARTED，招标项目永停在 PREPARING），就会形成每秒一次的事务风暴——
 * 实测 3.7 天里该查询跑了 1100+ 次、每秒约 3.9 次，最终耗尽 Druid 连接池（maxActive=20）
 * 使整个服务所有接口 500、健康检查超时。
 * 因此本类对"未就绪"记录记内存退避时间：首轮即退避到 {@code min-backoff-ms}，
 * 连续未就绪按倍数递增（上限 {@code max-backoff-ms}），到期才再试；
 * 一旦某实体成功流转，其退避计数清零。退避只在内存（重启即重置，单实例够用；
 * 多实例各自退避不冲突，因为 DONE/SKIPPED 幂等——与类头"重跑只是浪费资源"同一理由）。
 */
@Component
public class TimeEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeEventScheduler.class);

    @Resource
    private FlowTimerMapper flowTimerMapper;
    @Resource
    private FlowTransitionService flowTransitionService;

    /** 调度开关（默认关，须显式配置 bidding.flow.scheduler.enabled=true 才启用，符合 Job 安全门禁）；PROD 下即使 manual-advance-enabled=false，时间也能推动流转 */
    @Value("${bidding.flow.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /** 「状态未就绪」首次退避（ms，默认 60s）：避免永不就绪的记录被每秒重试打爆连接池 */
    @Value("${bidding.flow.timer-unready-backoff-ms:60000}")
    private long unreadyBackoffMs;

    /** 退避上限（ms，默认 30min）：连续未就绪时按 60s→2m→4m… 递增到此上限 */
    @Value("${bidding.flow.timer-unready-backoff-max-ms:1800000}")
    private long unreadyBackoffMaxMs;

    /**
     * 「状态未就绪」退避表：key=compositeKey(bizType,bizCode,event) → 下次可再试的时间 + 当前退避倍数。
     * 仅在内存（重启重置）；条目数=未就绪的 timer 数，量级极小（每实体最多 2~3 条）。
     * 已 DONE/SKIPPED 或实体成功流转的记录会被移除，避免无界增长。
     */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> unreadyBackoff = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 秒级扫描到期调度记录（fixedDelay：上一轮结束后再等 interval，默认 1 秒）。
     */
    @Scheduled(fixedDelayString = "${bidding.flow.timer-interval-ms:1000}")
    @TenantIgnore // G 轨调度表全局扫描，不受租户过滤；内部手动设置租户上下文执行流转
    public void triggerDueEvents() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            List<FlowTimerDO> due = flowTimerMapper.selectListByDue(LocalDateTime.now());
            if (due.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (FlowTimerDO timer : due) {
                // 退避期内跳过：这些记录到期但前置状态未就绪（如项目仍在 PREPARING），
                // 重试无意义且高频重试会耗尽连接池（见类注释「退避」）。
                long[] backoff = unreadyBackoff.get(backoffKey(timer));
                if (backoff != null && now < backoff[0]) {
                    continue;
                }
                processOne(timer);
            }
        } catch (Exception ex) {
            // 单轮扫描兜底：异常不逃逸出 @Scheduled（逃逸会被调度框架按策略跳过后续执行，任务静默失效）
            log.error("[时间驱动] 到期事件扫描失败：", ex);
        }
    }

    private void processOne(FlowTimerDO timer) {
        try {
            TenantContextHolder.setTenantId(1L); // 单租户环境（其余租户见文档）；G 轨表无租户列
            FlowTransitionReqVO req = new FlowTransitionReqVO();
            req.setBizType(timer.getBizType());
            req.setBizCode(timer.getBizCode());
            req.setEvent(timer.getEvent());
            req.setRemark("时间驱动自动流转（注册于 " + timer.getTriggerTime() + "）");
            flowTransitionService.transitionByTimer(req);
            mark(timer, FlowTimerStatusEnum.DONE);
            unreadyBackoff.remove(backoffKey(timer)); // 成功即清退避，避免表无界增长
            log.info("[时间驱动] {} {} {} → DONE", timer.getBizType(), timer.getBizCode(), timer.getEvent());
        } catch (ServiceException ex) {
            // 业务不可执行时区分两种情形：
            // ① 事件已过时（状态已越过目标/终态/实体删除）→ SKIPPED，不再重试；
            // ② 状态未就绪（事件前置状态未达，如 PREPARING 时 BIDDING_STARTED 到期）→ 保留 WAITING，状态就绪后下轮自动触发
            String current = flowTransitionService.getCurrentState(timer.getBizType(), timer.getBizCode());
            if (FlowTransitionServiceImpl.isEventPassed(timer.getEvent(), current)) {
                mark(timer, FlowTimerStatusEnum.SKIPPED);
                unreadyBackoff.remove(backoffKey(timer));
                log.warn("[时间驱动] {} {} {} → SKIPPED：{}（当前状态 {}）",
                        timer.getBizType(), timer.getBizCode(), timer.getEvent(), ex.getMessage(), current);
            } else {
                // 状态未就绪：保留 WAITING，但**必须退避**——若前置状态永不到达（例：草稿公告注册的
                // BIDDING_STARTED，项目永停在 PREPARING），不退避就会每秒重试并耗尽连接池。
                long nextDelay = scheduleBackoff(timer);
                log.info("[时间驱动] {} {} {} 到期但状态未就绪（当前 {}），保留 WAITING，{}ms 后再试",
                        timer.getBizType(), timer.getBizCode(), timer.getEvent(), current, nextDelay);
            }
        } catch (Exception ex) {
            // 意外异常同样 SKIPPED 防死循环，记录日志供排查
            mark(timer, FlowTimerStatusEnum.SKIPPED);
            log.error("[时间驱动] {} {} {} → SKIPPED(异常)：{}", timer.getBizType(), timer.getBizCode(), timer.getEvent(), ex.getMessage(), ex);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void mark(FlowTimerDO timer, FlowTimerStatusEnum status) {
        timer.setStatus(status.name());
        timer.setExecutedTime(LocalDateTime.now());
        flowTimerMapper.updateById(timer);
    }

    /** 退避表键：同一实体同一事件的 timer 唯一（register 按此幂等更新，不会重复建行）。 */
    private String backoffKey(FlowTimerDO timer) {
        return timer.getBizType() + "|" + timer.getBizCode() + "|" + timer.getEvent();
    }

    /**
     * 登记「状态未就绪」退避：首次 {@code unreadyBackoffMs}，此后每次未就绪按 2 倍递增，
     * 上限 {@code unreadyBackoffMaxMs}。
     *
     * <p>为什么指数递增而不是固定间隔：正常业务里"未就绪"应很快转就绪（发公告→ANNOUNCED 是秒级），
     * 首轮 60s 退避已足够吸收这个时间差；而连续多次未就绪往往意味着前置状态**永不到达**
     * （配置错误/脏数据），此时把频率压到 30min 一次，既保留自动恢复能力，又不占用资源。
     *
     * @return 本次登记的退避时长（ms），供日志观测
     */
    private long scheduleBackoff(FlowTimerDO timer) {
        long[] slot = unreadyBackoff.computeIfAbsent(backoffKey(timer), k -> new long[]{0L, 0L});
        // slot[1] = 已连续未就绪次数（用于计算倍增），slot[0] = 下次可再试的绝对时间戳
        long delay = slot[1] == 0 ? unreadyBackoffMs
                : Math.min(unreadyBackoffMs * (1L << Math.min(slot[1], 20)), unreadyBackoffMaxMs);
        slot[0] = System.currentTimeMillis() + delay;
        slot[1] = slot[1] + 1;
        return delay;
    }
}
