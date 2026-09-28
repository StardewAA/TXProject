package cn.iocoder.txgy.module.hrm.dal.mysql.agent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmRecruitAgentTaskMapper extends BaseMapperX<HrmRecruitAgentTaskDO> {

    /** 幂等查询：同一用户同一场景同一幂等键只应存在一个任务 */
    default HrmRecruitAgentTaskDO selectByUserSceneAndIdempotencyKey(Long userId, String scene,
                                                                    String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitAgentTaskDO>()
                .eq(HrmRecruitAgentTaskDO::getUserId, userId)
                .eq(HrmRecruitAgentTaskDO::getScene, scene)
                .eq(HrmRecruitAgentTaskDO::getIdempotencyKey, idempotencyKey));
    }

    default HrmRecruitAgentTaskDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitAgentTaskDO>()
                .eq(HrmRecruitAgentTaskDO::getId, id)
                .eq(HrmRecruitAgentTaskDO::getUserId, userId));
    }

    default HrmRecruitAgentTaskDO selectLatestByBiz(String bizType, Long bizId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitAgentTaskDO>()
                .eq(HrmRecruitAgentTaskDO::getBizType, bizType)
                .eq(HrmRecruitAgentTaskDO::getBizId, bizId)
                .orderByDesc(HrmRecruitAgentTaskDO::getId))
                .stream().findFirst().orElse(null);
    }

    /**
     * 批量查询一批业务对象下已成功的指定场景任务，按任务编号倒序。
     *
     * <p>调用方按 bizId 取第一条即为该业务对象的最新结果（失败任务不参与，
     * 避免上一次模型输出不合规就把列表分数清空）。</p>
     */
    default List<HrmRecruitAgentTaskDO> selectSucceededByBizIds(String bizType, String scene,
                                                                Collection<Long> bizIds) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitAgentTaskDO>()
                .eq(HrmRecruitAgentTaskDO::getBizType, bizType)
                .eq(HrmRecruitAgentTaskDO::getScene, scene)
                .eq(HrmRecruitAgentTaskDO::getStatus, HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus())
                .in(HrmRecruitAgentTaskDO::getBizId, bizIds)
                .orderByDesc(HrmRecruitAgentTaskDO::getId));
    }
}
