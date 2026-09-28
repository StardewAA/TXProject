package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidTaskShadowDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务影子 Mapper
 */
@Mapper
public interface BidTaskShadowMapper extends BaseMapperX<BidTaskShadowDO> {

    default BidTaskShadowDO selectByTaskId(String taskId) {
        return selectOne(new LambdaQueryWrapperX<BidTaskShadowDO>()
                .eq(BidTaskShadowDO::getTaskId, taskId));
    }
}
