package cn.iocoder.txgy.module.bidding.dal.mysql.processtrack;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessTrackDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投标流程跟踪 Mapper。
 */
@Mapper
public interface BiddingProcessTrackMapper extends BaseMapperX<BiddingProcessTrackDO> {

    default BiddingProcessTrackDO selectByUserIdAndSection(Long userId, String bidSectionCode) {
        return selectOne(BiddingProcessTrackDO::getUserId, userId,
                BiddingProcessTrackDO::getBidSectionCode, bidSectionCode);
    }

    default BiddingProcessTrackDO selectBySection(String bidSectionCode) {
        return selectOne(BiddingProcessTrackDO::getBidSectionCode, bidSectionCode);
    }

    default long countByState(String bidSectionCode, String state) {
        return selectCount(Wrappers.lambdaQuery(BiddingProcessTrackDO.class)
                .eq(BiddingProcessTrackDO::getBidSectionCode, bidSectionCode)
                .eq(BiddingProcessTrackDO::getCurrentState, state));
    }
}
