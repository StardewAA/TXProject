package cn.iocoder.txgy.module.mes.dal.mysql.wm.returnvendor;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.returnvendor.MesWmReturnVendorApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 采购退货审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmReturnVendorApprovalTaskMapper extends BaseMapperX<MesWmReturnVendorApprovalTaskDO> {

    default MesWmReturnVendorApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesWmReturnVendorApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesWmReturnVendorApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesWmReturnVendorApprovalTaskDO>()
                .eq(MesWmReturnVendorApprovalTaskDO::getStatus, MesWmReturnVendorApprovalTaskDO.STATUS_PENDING)
                .le(MesWmReturnVendorApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesWmReturnVendorApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
