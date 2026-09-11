package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectSyncTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 项目同步任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectSyncTaskMapper extends BaseMapperX<MesProProjectSyncTaskDO> {

    default MesProProjectSyncTaskDO selectBySaleOrderId(Long saleOrderId) {
        return selectOne(MesProProjectSyncTaskDO::getSaleOrderId, saleOrderId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesProProjectSyncTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesProProjectSyncTaskDO>()
                .eq(MesProProjectSyncTaskDO::getStatus, MesProProjectSyncTaskDO.STATUS_PENDING)
                .le(MesProProjectSyncTaskDO::getNextRetryTime, now)
                .orderByAsc(MesProProjectSyncTaskDO::getId)
                .last("LIMIT 50"));
    }

}
