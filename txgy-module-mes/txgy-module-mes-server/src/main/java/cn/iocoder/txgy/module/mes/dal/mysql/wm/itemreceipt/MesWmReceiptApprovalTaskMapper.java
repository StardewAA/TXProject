package cn.iocoder.txgy.module.mes.dal.mysql.wm.itemreceipt;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.itemreceipt.MesWmReceiptApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 入库单审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmReceiptApprovalTaskMapper extends BaseMapperX<MesWmReceiptApprovalTaskDO> {

    default MesWmReceiptApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesWmReceiptApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesWmReceiptApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesWmReceiptApprovalTaskDO>()
                .eq(MesWmReceiptApprovalTaskDO::getStatus, MesWmReceiptApprovalTaskDO.STATUS_PENDING)
                .le(MesWmReceiptApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesWmReceiptApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
