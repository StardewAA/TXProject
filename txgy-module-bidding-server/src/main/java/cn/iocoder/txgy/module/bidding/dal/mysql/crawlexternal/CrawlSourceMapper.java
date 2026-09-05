package cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §15.1（G 轨）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集源配置（白名单源：URL/频控/通道/策略） Mapper（G 轨，不拼租户条件）。
 */
@Mapper
public interface CrawlSourceMapper extends BaseMapperX<CrawlSourceDO> {
}
