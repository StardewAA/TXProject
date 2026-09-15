package cn.iocoder.txgy.module.mes.dal.mysql.pro.feedback;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.feedback.MesFeedbackApprovalTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 报工审批结果任务 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesFeedbackApprovalTaskMapper extends BaseMapperX<MesFeedbackApprovalTaskDO> {

    default MesFeedbackApprovalTaskDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(MesFeedbackApprovalTaskDO::getProcessInstanceId, processInstanceId);
    }

    /**
     * 查询到期待处理/重试的任务（跨租户扫描，由调用方切换租户后逐条处理）
     *
     * @param now 当前时间
     * @return 任务列表
     */
    default List<MesFeedbackApprovalTaskDO> selectPendingList(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MesFeedbackApprovalTaskDO>()
                .eq(MesFeedbackApprovalTaskDO::getStatus, MesFeedbackApprovalTaskDO.STATUS_PENDING)
                .le(MesFeedbackApprovalTaskDO::getNextRetryTime, now)
                .orderByAsc(MesFeedbackApprovalTaskDO::getId)
                .last("LIMIT 50"));
    }

}
