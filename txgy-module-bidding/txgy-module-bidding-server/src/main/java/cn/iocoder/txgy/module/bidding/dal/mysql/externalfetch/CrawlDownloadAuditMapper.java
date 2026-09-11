package cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch;

// 规范依据：用户口径 2026-09-10「文件完整性与状态监测」。

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlDownloadAuditDO;

/**
 * 原文下载审计 Mapper（只追加；监控查询走 BaseMapperX 的 selectPage/selectList）。
 */
public interface CrawlDownloadAuditMapper extends BaseMapperX<CrawlDownloadAuditDO> {
}
