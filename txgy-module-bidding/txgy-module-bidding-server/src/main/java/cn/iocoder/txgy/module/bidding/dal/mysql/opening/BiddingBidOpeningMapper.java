package cn.iocoder.txgy.module.bidding.dal.mysql.opening;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingBidOpeningDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开标记录（开标房间主记录）Mapper。
 */
@Mapper
public interface BiddingBidOpeningMapper extends BaseMapperX<BiddingBidOpeningDO> {

    default BiddingBidOpeningDO selectByTenderProjectCode(String tenderProjectCode) {
        return selectOne(BiddingBidOpeningDO::getTenderProjectCode, tenderProjectCode);
    }
}
