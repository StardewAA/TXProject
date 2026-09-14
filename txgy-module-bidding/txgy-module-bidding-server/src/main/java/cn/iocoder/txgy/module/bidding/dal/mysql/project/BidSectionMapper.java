package cn.iocoder.txgy.module.bidding.dal.mysql.project;

// [规范依据]《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）
// [业务目标] 标段（包） 主档数据访问（租户过滤由框架拦截器自动追加）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BidSectionMapper extends BaseMapperX<BidSectionDO> {
}
