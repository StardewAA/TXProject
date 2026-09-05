package cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §15.1（G 轨）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集任务（分布式：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED，断点/重试≤3/worker 归属） Mapper（G 轨，不拼租户条件）。
 */
@Mapper
public interface CrawlTaskMapper extends BaseMapperX<CrawlTaskDO> {
}
