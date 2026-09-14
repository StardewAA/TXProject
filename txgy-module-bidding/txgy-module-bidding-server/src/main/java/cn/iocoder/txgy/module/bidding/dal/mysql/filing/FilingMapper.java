package cn.iocoder.txgy.module.bidding.dal.mysql.filing;
// 规范依据：《招投标模块开发规范》§4.2

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.filing.FilingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备案受理记录 Mapper。
 */
@Mapper
public interface FilingMapper extends BaseMapperX<FilingDO> {
}
