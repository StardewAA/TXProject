package cn.iocoder.txgy.module.cms.service.sync;

/**
 * MySQL 任务驱动的索引同步契约。没有实现、定时调度或 Streams 生产/消费。
 * enqueue 实现必须参与业务记录修改的同一事务，不能在提交后才记录任务。
 * processPending 实现应校验 limit、领取任务租约、回源检查最新状态，幂等更新并失败重试。
 * 旧修订号不得覆盖新数据；删除需保留版本依据；不可清空其他业务或租户索引。
 */
public interface CmsIndexSyncService {
    void enqueue(CmsIndexSyncTask task);
    int processPending(int limit);
}
