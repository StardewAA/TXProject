package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidEnterpriseFactDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 企业事实 Mapper
 */
@Mapper
public interface BidEnterpriseFactMapper extends BaseMapperX<BidEnterpriseFactDO> {

    default List<BidEnterpriseFactDO> selectListByTypes(List<String> types) {
        return selectList(new LambdaQueryWrapperX<BidEnterpriseFactDO>()
                .inIfPresent(BidEnterpriseFactDO::getType, types));
    }
}
