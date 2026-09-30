package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 一键生成任务台账 Mapper */
@Mapper
public interface BidAgentTaskMapper extends BaseMapperX<BidAgentTaskDO> {

    default BidAgentTaskDO selectByTaskNo(String taskNo) {
        return selectOne(new LambdaQueryWrapperX<BidAgentTaskDO>().eq(BidAgentTaskDO::getTaskNo, taskNo));
    }

    default BidAgentTaskDO selectByAgentTaskId(String agentTaskId) {
        return selectOne(new LambdaQueryWrapperX<BidAgentTaskDO>().eq(BidAgentTaskDO::getAgentTaskId, agentTaskId));
    }

    default PageResult<BidAgentTaskDO> selectPage(PageParam pageParam, Long projectId, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<BidAgentTaskDO>()
                .eqIfPresent(BidAgentTaskDO::getProjectId, projectId)
                .eqIfPresent(BidAgentTaskDO::getStatus, status)
                .orderByDesc(BidAgentTaskDO::getId));
    }

    /** 同项目+标段是否已有进行中的任务（幂等/防重复发起） */
    default List<BidAgentTaskDO> selectRunningList(Long projectId, String bidSectionCode) {
        return selectList(new LambdaQueryWrapperX<BidAgentTaskDO>()
                .eq(BidAgentTaskDO::getProjectId, projectId)
                .eq(BidAgentTaskDO::getBidSectionCode, bidSectionCode)
                .in(BidAgentTaskDO::getStatus, List.of("PENDING", "RUNNING")));
    }
}
