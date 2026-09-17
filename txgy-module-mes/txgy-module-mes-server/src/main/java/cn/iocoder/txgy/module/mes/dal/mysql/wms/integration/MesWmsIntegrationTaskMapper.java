package cn.iocoder.txgy.module.mes.dal.mysql.wms.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES↔WMS 集成任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmsIntegrationTaskMapper extends BaseMapperX<MesWmsIntegrationTaskDO> {

    /** 取待发送任务（到达处理时间的最早 N 条） */
    default List<MesWmsIntegrationTaskDO> selectPendingSendList(int limit) {
        return selectList(new LambdaQueryWrapperX<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getStatus, 0)
                .and(w -> w.isNull(MesWmsIntegrationTaskDO::getNextRetryTime)
                        .or().le(MesWmsIntegrationTaskDO::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(MesWmsIntegrationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    /** 取已受理/执行中的任务（轮询结果） */
    default List<MesWmsIntegrationTaskDO> selectAcceptedOrExecutingList(int limit) {
        return selectList(new LambdaQueryWrapperX<MesWmsIntegrationTaskDO>()
                .in(MesWmsIntegrationTaskDO::getStatus, 1, 2)
                .and(w -> w.isNull(MesWmsIntegrationTaskDO::getNextRetryTime)
                        .or().le(MesWmsIntegrationTaskDO::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(MesWmsIntegrationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    /** 按业务单据查询（幂等检查/状态展示） */
    default MesWmsIntegrationTaskDO selectBySourceOrder(String orderType, Long sourceOrderId) {
        return selectOne(new LambdaQueryWrapperX<MesWmsIntegrationTaskDO>()
                .eq(MesWmsIntegrationTaskDO::getOrderType, orderType)
                .eq(MesWmsIntegrationTaskDO::getSourceOrderId, sourceOrderId)
                .orderByDesc(MesWmsIntegrationTaskDO::getId)
                .last("LIMIT 1"));
    }

}
