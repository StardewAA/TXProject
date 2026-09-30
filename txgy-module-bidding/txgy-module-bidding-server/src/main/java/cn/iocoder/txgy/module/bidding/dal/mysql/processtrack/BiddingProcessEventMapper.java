package cn.iocoder.txgy.module.bidding.dal.mysql.processtrack;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessEventDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 投标流程事件记录 Mapper。
 */
@Mapper
public interface BiddingProcessEventMapper extends BaseMapperX<BiddingProcessEventDO> {

    default List<BiddingProcessEventDO> selectListByTrackId(Long trackId) {
        return selectList(Wrappers.lambdaQuery(BiddingProcessEventDO.class)
                .eq(BiddingProcessEventDO::getTrackId, trackId)
                .orderByAsc(BiddingProcessEventDO::getEventTime));
    }
}
