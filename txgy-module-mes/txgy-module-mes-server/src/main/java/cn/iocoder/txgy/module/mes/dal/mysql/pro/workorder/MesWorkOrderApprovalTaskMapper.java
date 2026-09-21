package cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesWorkOrderApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 生产工单审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWorkOrderApprovalTaskMapper extends BaseMapperX<MesWorkOrderApprovalTaskDO> {

    default MesWorkOrderApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesWorkOrderApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesWorkOrderApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesWorkOrderApprovalTaskDO>()
                .eq(MesWorkOrderApprovalTaskDO::getStatus, MesWorkOrderApprovalTaskDO.STATUS_PENDING)
                .le(MesWorkOrderApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesWorkOrderApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
