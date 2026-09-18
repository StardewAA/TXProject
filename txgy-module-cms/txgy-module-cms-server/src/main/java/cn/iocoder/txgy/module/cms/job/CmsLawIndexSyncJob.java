package cn.iocoder.txgy.module.cms.job;

/**
 * 法规索引同步任务契约，目前未注册定时任务或启动后台作业。
 * 后续实现：读取事务内记录的同步任务，按资料编号幂等更新，失败重试。
 */
public interface CmsLawIndexSyncJob {

    void synchronizePendingTasks();

}
