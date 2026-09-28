package cn.iocoder.txgy.module.bidding.dal.mysql.opening;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingOpeningMemberDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 开标房间成员 Mapper。
 */
@Mapper
public interface BiddingOpeningMemberMapper extends BaseMapperX<BiddingOpeningMemberDO> {

    default List<BiddingOpeningMemberDO> selectListByOpeningId(Long openingId) {
        return selectList(Wrappers.lambdaQuery(BiddingOpeningMemberDO.class)
                .eq(BiddingOpeningMemberDO::getOpeningId, openingId)
                .orderByAsc(BiddingOpeningMemberDO::getEnterTime));
    }

    default BiddingOpeningMemberDO selectByOpeningIdAndUser(Long openingId, Long userId) {
        return selectOne(BiddingOpeningMemberDO::getOpeningId, openingId,
                BiddingOpeningMemberDO::getUserId, userId);
    }
}
