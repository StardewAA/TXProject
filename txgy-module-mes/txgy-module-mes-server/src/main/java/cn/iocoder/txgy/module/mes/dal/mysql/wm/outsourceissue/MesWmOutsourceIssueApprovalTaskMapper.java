package cn.iocoder.txgy.module.mes.dal.mysql.wm.outsourceissue;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.outsourceissue.MesWmOutsourceIssueApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 委外发料审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmOutsourceIssueApprovalTaskMapper extends BaseMapperX<MesWmOutsourceIssueApprovalTaskDO> {

    default MesWmOutsourceIssueApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesWmOutsourceIssueApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesWmOutsourceIssueApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesWmOutsourceIssueApprovalTaskDO>()
                .eq(MesWmOutsourceIssueApprovalTaskDO::getStatus, MesWmOutsourceIssueApprovalTaskDO.STATUS_PENDING)
                .le(MesWmOutsourceIssueApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesWmOutsourceIssueApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
