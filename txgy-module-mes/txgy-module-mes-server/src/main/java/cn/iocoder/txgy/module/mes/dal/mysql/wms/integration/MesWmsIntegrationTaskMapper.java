package cn.iocoder.txgy.module.mes.dal.mysql.wms.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 物理删除指定业务单据的集成任务（绕过逻辑删除，供"取消后重建"使用）。
     *
     * <p>恢复领料时需先删旧任务、再按同一 requestId 重建。若走 {@link BaseMapperX#deleteById}，
     * 框架的逻辑删除会把它变成 UPDATE deleted=1，记录仍占用 request_id 唯一键，
     * 导致重建时 DuplicateKeyException，表现为"旧任务被删、新任务未建"的静默失败。
     * 故此处使用原生 DELETE 做物理删除，且不按 deleted=0 预筛选，避免历史逻辑删除记录残留。</p>
     */
    @Delete("DELETE FROM mes_wms_integration_task WHERE order_type = #{orderType} "
            + "AND source_order_id = #{sourceOrderId} AND tenant_id = #{tenantId}")
    int deleteBySourceOrderPhysically(@Param("orderType") String orderType,
                                      @Param("sourceOrderId") Long sourceOrderId,
                                      @Param("tenantId") Long tenantId);

}
