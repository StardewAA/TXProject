package cn.iocoder.txgy.module.bidding.service.externalfetch.audit;

// 规范依据：用户口径 2026-09-10「在测试方案的设计上，必须增加严格的监控指标，重点包含：
//   下载速度监测（实时监测并记录下载速率，确保带宽使用在合理范围内，防范异常流量）、
//   文件完整性与状态监测（严格校验下载文件的完整性（如 MD5/SHA 校验）、文件大小、格式合规性，以及存储状态）」。
// 业务目标：把每次原文下载的"量、速、摘要、结论、存储状态"统一落审计表并即时打日志，
//   使监控既能实时看（日志 WARN）也能事后查（SQL 台账/趋势）。
// 为什么审计失败不影响采集：监控是旁路能力——记录失败不能反过来阻断业务；
//   但记录缺失必须显式报错，避免"以为在监控其实没数据"。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlDownloadAuditDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlDownloadAuditMapper;
import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 原文下载审计与速度监控记录器（只追加）。
 */
@Slf4j
@Component
public class CrawlDownloadAuditor {

    /** 存储状态：未归档 */
    public static final String STORAGE_NONE = "NONE";
    /** 存储状态：已归档且校验通过 */
    public static final String STORAGE_REGISTERED = "REGISTERED";
    /** 存储状态：归档失败/校验不符 */
    public static final String STORAGE_FAILED = "FAILED";

    @Resource
    private CrawlDownloadAuditMapper auditMapper;
    @Resource
    private CrawlHttpProperties props;

    /**
     * 记录一次原文下载（含速度告警）。
     *
     * @param byteSize  下载字节数（null=未取得）
     * @param durationMs 耗时（毫秒）
     */
    public void record(Long taskId, String sourceCode, String url, String kind, Integer httpStatus,
                       Long byteSize, Long durationMs, String sha256, String verdict,
                       String storageStatus, Long fileId, String message) {
        Long speedBps = (byteSize == null || durationMs == null || durationMs <= 0)
                ? null : byteSize * 1000L / durationMs;
        // 速度监控（带宽异常/被限速）：实时告警，阈值来自 bidding.crawl.http.speed-*-bps
        if (speedBps != null && byteSize != null && byteSize > 0) {
            long warn = props == null ? 2L * 1024 * 1024 : props.getSpeedWarnBps();
            long slow = props == null ? 2L * 1024 : props.getSpeedSlowWarnBps();
            if (speedBps > warn) {
                log.warn("[download-monitor][速度偏高] {} B/s（阈值 {} B/s）kind={} bytes={} 耗时={}ms url={}",
                        speedBps, warn, kind, byteSize, durationMs, url);
            } else if (speedBps < slow) {
                log.warn("[download-monitor][速度偏低] {} B/s（阈值 {} B/s）kind={} bytes={} 耗时={}ms url={}",
                        speedBps, slow, kind, byteSize, durationMs, url);
            }
        }
        if (verdict != null && !"PASS".equals(verdict)) {
            log.warn("[download-monitor][完整性校验未通过] verdict={} bytes={} url={} 原因={}",
                    verdict, byteSize, url, message);
        }
        try {
            CrawlDownloadAuditDO row = new CrawlDownloadAuditDO();
            row.setTaskId(taskId);
            row.setSourceCode(sourceCode);
            row.setUrl(url == null ? "" : (url.length() <= 1000 ? url : url.substring(0, 1000)));
            row.setDownloadKind(kind);
            row.setHttpStatus(httpStatus);
            row.setByteSize(byteSize);
            row.setDurationMs(durationMs);
            row.setSpeedBps(speedBps);
            row.setSha256(sha256);
            row.setIntegrityVerdict(verdict);
            row.setStorageStatus(storageStatus);
            row.setFileId(fileId);
            row.setMessage(message == null ? null : (message.length() <= 500 ? message : message.substring(0, 500)));
            auditMapper.insert(row);
            log.info("[download-monitor][记录] kind={} status={} bytes={} 耗时={}ms 速率={}B/s verdict={} storage={} fileId={}",
                    kind, httpStatus, byteSize, durationMs, speedBps, verdict, storageStatus, fileId);
        } catch (Exception e) {
            // 旁路能力：审计写失败不阻断采集，但必须显式暴露（否则"以为在监控其实没数据"）
            log.error("[download-monitor][审计写入失败] kind={} url={} err={}", kind, url, e.getMessage());
        }
    }

}
