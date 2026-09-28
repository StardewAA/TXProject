package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentEvalCaseDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BidAgentEvalCaseMapper extends BaseMapperX<BidAgentEvalCaseDO> {

    default PageResult<BidAgentEvalCaseDO> selectPage(BidAgentEvalVO.CasePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidAgentEvalCaseDO>()
                .likeIfPresent(BidAgentEvalCaseDO::getName, reqVO.getName())
                .eqIfPresent(BidAgentEvalCaseDO::getEnabled, reqVO.getEnabled())
                .orderByDesc(BidAgentEvalCaseDO::getId));
    }

    default List<BidAgentEvalCaseDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<BidAgentEvalCaseDO>()
                .eq(BidAgentEvalCaseDO::getEnabled, 1)
                .orderByDesc(BidAgentEvalCaseDO::getId));
    }

    /** 按编号批量查询（跑批用，避免逐条 selectById 的 N+1） */
    default List<BidAgentEvalCaseDO> selectListByIds(java.util.Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<BidAgentEvalCaseDO>()
                .in(BidAgentEvalCaseDO::getId, ids)
                .orderByDesc(BidAgentEvalCaseDO::getId));
    }
}
