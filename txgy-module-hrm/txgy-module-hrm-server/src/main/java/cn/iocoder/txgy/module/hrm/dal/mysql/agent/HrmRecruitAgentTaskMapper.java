package cn.iocoder.txgy.module.hrm.dal.mysql.agent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import org.apache.ibatis.annotations.Mapper;

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
}
