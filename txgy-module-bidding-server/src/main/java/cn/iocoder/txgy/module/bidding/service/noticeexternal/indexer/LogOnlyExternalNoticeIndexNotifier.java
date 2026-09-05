package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;
// 规范依据：设计文档 v0.6 §7.6/§15.6（ES 建索引事件预留：依赖接入后替换为 MQ 生产者 + 独立索引任务）

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ES 索引通知器占位实现：仅记录日志。
 *
 * <p>待接入项（见设计 §7.6 预留）：
 * <ol>
 *   <li>接入 starter-mq（Redis Stream topic：ext-notice.index），本方法改为发布事件；</li>
 *   <li>独立消费者调 txgy elasticsearch 封装建索引（title/标题+正文抽取文本/项目/标段等可检索字段）；</li>
 *   <li>补偿：定时扫描 ACTIVE 且未索引(如按索引标记/时间窗口)的主档重发事件。</li>
 * </ol>
 * 更换实现即可切换，调用方（审核服务）无感知。
 */
@Slf4j
@Component
public class LogOnlyExternalNoticeIndexNotifier implements ExternalNoticeIndexNotifier {

    @Override
    public void indexNotice(NoticeExternalDO notice) {
        // 预留：发布 MQ 事件 ext-notice.index（Redis Stream，3 次重试→死信转人工复核）
        log.info("[indexNotice][noticeId={} title={}] ES 索引事件预留（MQ 未接入，占位日志）",
                notice.getId(), notice.getTitle());
    }

}
