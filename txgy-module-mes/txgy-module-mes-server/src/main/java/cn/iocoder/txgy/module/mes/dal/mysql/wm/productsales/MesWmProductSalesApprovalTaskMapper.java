package cn.iocoder.txgy.module.mes.dal.mysql.wm.productsales;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productsales.MesWmProductSalesApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 销售出库审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmProductSalesApprovalTaskMapper extends BaseMapperX<MesWmProductSalesApprovalTaskDO> {

    default MesWmProductSalesApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesWmProductSalesApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesWmProductSalesApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesWmProductSalesApprovalTaskDO>()
                .eq(MesWmProductSalesApprovalTaskDO::getStatus, MesWmProductSalesApprovalTaskDO.STATUS_PENDING)
                .le(MesWmProductSalesApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesWmProductSalesApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
