package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;
// 规范依据：设计文档 v0.6 §7.6/§15.6（ES 建索引事件预留：依赖接入后替换为 MQ 生产者 + 独立索引任务）

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ES 索引通知器降级实现（bidding.es.enabled=false / 未配置时装配）：仅记录日志。
 * <p>与 {@link EsExternalNoticeIndexNotifier} 按 bidding.es.enabled 条件互斥；调用方（审核服务）无感知。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LogOnlyExternalNoticeIndexNotifier implements ExternalNoticeIndexNotifier {

    @Override
    public void removeNotice(Long noticeId) {
        log.info("[removeNotice][noticeId={}] ES 删除事件预留（MQ 未接入，占位日志）", noticeId);
    }

    @Override
    public void indexNotice(NoticeExternalDO notice) {
        // 预留：发布 MQ 事件 ext-notice.index（Redis Stream，3 次重试→死信转人工复核）
        log.info("[indexNotice][noticeId={} title={}] ES 索引事件预留（MQ 未接入，占位日志）",
                notice.getId(), notice.getTitle());
    }

}
