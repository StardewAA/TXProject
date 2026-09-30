package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentProfileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BidAgentProfileMapper extends BaseMapperX<BidAgentProfileDO> {

    default BidAgentProfileDO selectByTaskType(String taskType) {
        return selectOne(new LambdaQueryWrapperX<BidAgentProfileDO>()
                .eq(BidAgentProfileDO::getTaskType, taskType));
    }

    default List<BidAgentProfileDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<BidAgentProfileDO>()
                .orderByAsc(BidAgentProfileDO::getSort));
    }
}
