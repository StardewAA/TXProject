package cn.iocoder.txgy.module.bidding.service.externalfetch.schema;

// 规范依据：《招投标模块开发规范》§4.4（故障可见）；用户反馈 2026-09-11：
//   「别人用我的 sql，说没有这个表：Table 'ruoyi-vue-pro.bidding_crawl_download_gate' doesn't exist
//     → 任务被判定失败 → 120/240 秒后重试 → 无限循环」。
// 原因（实测复盘）：① 早期版本的 sql/master-bidding-sync.sql 里没有闸位表（该表 2026-09-10 才并入主包，
//   提交 40b8ef0d；此前版本 0 处），别人用的是旧包；② 即使是最新包，mysql 客户端「遇到第一个错误就中止」，
//   旧包把闸位表放在菜单段之后 —— 目标库若没有 system_menu（菜单属 system 服务库），菜单段报错即中止，
//   后面的闸位表/审计表都不会建。
// 业务目标：把这类"部署期缺表"从**运行期无限重试**变成**启动期一句话说清 + 停掉空转调度**。

import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.BiddingSchemaMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模块库表启动自检（ApplicationRunner：全部 Bean 就绪后、应用对外就绪前执行）。
 *
 * <p>行为：
 * <ol>
 *   <li>盘点 sql/master-bidding-sync.sql §① 的全部业务表；齐备则打一条 OK 日志；</li>
 *   <li>有缺失：打 ERROR 日志，内容含**缺哪些表**、**该执行哪条命令**，以及旧包/中止执行的坑位提示；</li>
 *   <li>若缺的是爬虫关键表（源/任务/批次/限速闸/审计）：调用 {@link BiddingSchemaGuard} 标记不可用，
 *       由调度入口停掉爬虫——缺表时每个任务都会在闸位处抛错、退避后进死信，新一轮调度再造新任务，
 *       表现为"无限重试且毫无进展"；停调度比空转刷死信更诚实（其它业务域不受影响，服务照常提供）。</li>
 * </ol>
 *
 * <p>为什么不直接 fail-fast 拒绝启动：本模块还承载项目/标段、主体授权等与爬虫无关的业务，
 * 只缺爬虫表时让整服务起不来会连带影响无关功能；因此采用"报错 + 精准熔断"。
 * 需要严格 fail-fast 的环境可置 bidding.schema.strict=true。
 */
@Slf4j
@Component
public class BiddingSchemaHealthCheck implements ApplicationRunner {

    /** 模块全部业务表：与 sql/master-bidding-sync.sql §① 一一对应（改结构时两处同步）；包内可见=供单测引用，避免测试再抄一份 */
    static final List<String> ALL_TABLES = List.of(
            "bidding_fetch_job", "bidding_fetch_source", "bidding_fetch_task", "bidding_filing",
            "bidding_follow_item", "bidding_follow_item_event", "bidding_notice", "bidding_notice_content",
            "bidding_notice_external", "bidding_notice_external_attachment", "bidding_notice_external_meta",
            "bidding_notice_subscription", "bidding_notice_subscription_event", "bidding_subject_auth",
            "bidding_subject_profile", "bidding_subject_user", "bidding_project", "bidding_tender_project",
            "bidding_bid_section", "bidding_crawl_download_gate", "bidding_crawl_download_audit");

    /** 爬虫关键表：缺任意一张，采集链路必然反复失败（源配置 / 任务 / 批次 / 限速闸 / 审计） */
    private static final List<String> CRAWL_CRITICAL = List.of(
            "bidding_fetch_source", "bidding_fetch_task", "bidding_fetch_job",
            "bidding_crawl_download_gate", "bidding_crawl_download_audit");

    /** 严格模式：缺表即拒绝启动（默认 false = 报错 + 精准熔断，不影响无关业务域） */
    @Value("${bidding.schema.strict:false}")
    private boolean strict;

    @Resource
    private BiddingSchemaMapper biddingSchemaMapper;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> existing;
        try {
            existing = new LinkedHashSet<>(biddingSchemaMapper.selectExistingTables(ALL_TABLES));
        } catch (Exception e) {
            log.error("[BiddingSchemaHealthCheck][FAIL] 库表自检无法执行（数据库不可用或权限不足）：{}", e.getMessage(), e);
            BiddingSchemaGuard.markCrawlSchemaUnavailable("库表自检无法执行：" + e.getMessage());
            return;
        }
        List<String> missing = ALL_TABLES.stream().filter(t -> !existing.contains(t)).toList();
        if (missing.isEmpty()) {
            log.info("[BiddingSchemaHealthCheck][OK] 业务表 {} 张齐备", ALL_TABLES.size());
            return;
        }

        List<String> missingCrawl = missing.stream().filter(CRAWL_CRITICAL::contains).toList();
        log.error("[BiddingSchemaHealthCheck][FAIL] 缺少 {} 张业务表：{}", missing.size(), missing);
        log.error("[BiddingSchemaHealthCheck][FIX] 修复命令（幂等，可重复执行；建议先备份）：");
        log.error("    docker exec -i txgy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro"
                + " < sql/master-bidding-sync.sql");
        log.error("  或仅补缺表：sql/history/2026-09-10-crawl-download-gate.sql（限速闸）、"
                + "2026-09-10-crawl-download-audit.sql（审计）");
        log.error("  常见原因：① 用的是旧版 master-bidding-sync.sql（限速闸/审计表 2026-09-10 才并入主包）；"
                + "② mysql 客户端遇到第一个错误就中止——旧包把这两张表排在菜单段之后，目标库没有 system_menu 时"
                + "菜单段报错即中止，后面的表就都没建。");

        if (!missingCrawl.isEmpty()) {
            String reason = "缺少爬虫关键表 " + missingCrawl + "；请执行 sql/master-bidding-sync.sql 后重启";
            BiddingSchemaGuard.markCrawlSchemaUnavailable(reason);
            log.error("[BiddingSchemaHealthCheck][STOP-CRAWL] 已停止爬虫调度：{}", reason);
            if (strict) {
                throw new IllegalStateException("bidding 库表自检未通过（bidding.schema.strict=true）：" + missing);
            }
        }
    }

}
