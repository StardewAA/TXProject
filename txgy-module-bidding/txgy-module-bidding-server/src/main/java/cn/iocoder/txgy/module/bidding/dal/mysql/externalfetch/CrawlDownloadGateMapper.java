package cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch;

// 规范依据：《外部信息获取 v1.0》§11 频控；用户口径 2026-09-10「每次下载原文文件前必须强制休眠等待」。

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlDownloadGateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 原文下载限速闸 Mapper：占位与初始化均以 SQL 原子语义实现。
 */
public interface CrawlDownloadGateMapper extends BaseMapperX<CrawlDownloadGateDO> {

    /**
     * 原子占位：仅当"距上次下载已满 intervalSeconds"时才更新成功。
     *
     * <p>并发语义：InnoDB 行锁使并发 UPDATE 串行执行，后到者读到已更新的 last_download_at → 命中 0 行；
     * 因此 affected=1 是"本次获得下载权"的唯一凭据（跨实例、跨重启有效）。
     *
     * @return 1=占位成功（可下载）；0=未到间隔（不得下载）
     */
    @Update("UPDATE bidding_crawl_download_gate "
            + "SET last_download_at = NOW(), last_task_id = #{taskId}, last_url = #{url}, "
            + "    updater = 'download-gate', update_time = NOW() "
            + "WHERE scope_key = #{scopeKey} AND deleted = b'0' "
            + "  AND (last_download_at IS NULL OR last_download_at <= DATE_SUB(NOW(), INTERVAL #{intervalSeconds} SECOND))")
    int claim(@Param("scopeKey") String scopeKey,
              @Param("intervalSeconds") int intervalSeconds,
              @Param("taskId") Long taskId,
              @Param("url") String url);

    /** 幂等补建闸位（闸位行被误删时自愈；已存在不产生任何变更） */
    @Insert("INSERT IGNORE INTO bidding_crawl_download_gate "
            + "(id, scope_key, last_download_at, creator, create_time, updater, update_time, deleted) "
            + "VALUES (#{id}, #{scopeKey}, NULL, 'download-gate', NOW(), 'download-gate', NOW(), b'0')")
    int insertIgnore(@Param("id") Long id, @Param("scopeKey") String scopeKey);

}
