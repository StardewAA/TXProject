package cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §15.1（G 轨）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlJobDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集批次（调度/人工跑批汇总） Mapper（G 轨，不拼租户条件）。
 */
@Mapper
public interface CrawlJobMapper extends BaseMapperX<CrawlJobDO> {
}
