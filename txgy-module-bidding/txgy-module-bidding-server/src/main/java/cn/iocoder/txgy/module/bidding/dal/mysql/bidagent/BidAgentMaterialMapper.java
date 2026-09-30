package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMaterialVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMaterialDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BidAgentMaterialMapper extends BaseMapperX<BidAgentMaterialDO> {

    default PageResult<BidAgentMaterialDO> selectPage(BidAgentMaterialVO.PageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidAgentMaterialDO>()
                .eqIfPresent(BidAgentMaterialDO::getDocType, reqVO.getDocType())
                .eqIfPresent(BidAgentMaterialDO::getBidSectionCode, reqVO.getBidSectionCode())
                .eqIfPresent(BidAgentMaterialDO::getIndexStatus, reqVO.getIndexStatus())
                .likeIfPresent(BidAgentMaterialDO::getName, reqVO.getName())
                .orderByDesc(BidAgentMaterialDO::getId));
    }
}
