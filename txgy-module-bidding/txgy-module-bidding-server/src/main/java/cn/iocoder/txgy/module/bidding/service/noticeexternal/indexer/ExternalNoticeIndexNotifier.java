package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;
// 规范依据：设计文档 v0.6 §7.6/§15.6（external 审核转 ACTIVE → MQ 事件建 ES 索引，预留；§7.6 补偿扫描为 P2 未实现）；红线 8（不开放业务 LIKE，关键词走 ES）

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;

/**
 * 外部公告 ES 索引通知器（事件预留）。
 *
 * <p>设计：审核转 ACTIVE 后经 MQ（starter-mq Redis Stream，依赖接入后启用）发事件建索引，
 * 失败靠“未索引补偿扫描”（按 id/生效时间窗口回捞）兜底，不阻塞审核主流程——
 * <b>注意：MQ 事件链与补偿扫描均为 §7.6 P2 目标、尚未实现</b>，当前失败仅记日志，需人工复核后重放。
 * 当前版本以本地同步回调 + 日志占位实现，保证审核→索引链路可替换、可测试。
 */
public interface ExternalNoticeIndexNotifier {

    /**
     * 索引（或更新）一条 ACTIVE 外部公告。
     *
     * @param notice 已转 ACTIVE 的主档（含 extraction_*，关键词字段由其派生）
     */
    void indexNotice(NoticeExternalDO notice);

    /**
     * 删除索引文档（2026-09-10 补全：下线/驳回后从检索索引移除，保持展示与索引一致；
     * 文档不存在=404 视为成功——幂等重放安全）。
     *
     * @param noticeId 公告 id
     */
    void removeNotice(Long noticeId);

}
