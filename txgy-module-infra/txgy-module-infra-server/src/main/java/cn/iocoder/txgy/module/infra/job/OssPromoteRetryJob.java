package cn.iocoder.txgy.module.infra.job;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.infra.service.file.FileService;
import cn.iocoder.txgy.module.infra.service.file.OssPromoteRetryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;

/**
 * OSS 提升补传定时任务（可安全重试）
 *
 * <p>背景：已确认公开的文件在绑定时会尝试提升到公开 OSS；若当时 OSS 网络抖动（TLS 握手中断），
 * 提升会失败并保留在 master（仍经网关匿名可读）。本任务周期性扫描「已确认公开但未进入 OSS」
 * 的文件并补传，使其在网络恢复后真正发布到公共桶。</p>
 *
 * <p>安全性：
 * 1. 仅处理 PUBLIC 文件，绝不触碰私有文件、绝不会把私有文件迁移到公共桶；
 * 2. 已在 OSS 的文件幂等跳过；
 * 3. 失败仅记录失败次数与末次错误，不抛异常、不阻断业务；
 * 4. 由 {@code txgy.file.oss-promote.scheduled-retry-enabled} 控制（默认开启），可随时关闭。</p>
 *
 * @author OPENLAB BS
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "txgy.file.oss-promote", name = "scheduled-retry-enabled",
        havingValue = "true", matchIfMissing = true)
public class OssPromoteRetryJob {

    @Resource
    private FileService fileService;

    /**
     * 每 5 分钟补传一次（fixedDelay：上一轮结束后间隔 5 分钟再跑，避免任务堆积）。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void retry() {
        try {
            OssPromoteRetryResult result = fileService.retryPublicOssPromotion(200);
            if (result.getSuccess() > 0 || result.getFailed() > 0) {
                log.info("[OssPromoteRetryJob] 补传公开文件到 OSS：total={} success={} failed={} skipped={}",
                        result.getTotal(), result.getSuccess(), result.getFailed(), result.getSkipped());
            }
            if (result.getFailed() > 0 && StrUtil.isNotEmpty(result.getMessage())) {
                log.warn("[OssPromoteRetryJob] 仍有失败（OSS 可能不可达）：{}", result.getMessage());
            }
        } catch (Exception ex) {
            log.warn("[OssPromoteRetryJob] 补传任务异常", ex);
        }
    }
}
