package cn.iocoder.txgy.module.hrm.dal.mysql.agent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentOptimizeItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmRecruitAgentOptimizeItemMapper extends BaseMapperX<HrmRecruitAgentOptimizeItemDO> {

    /** 按任务查询优化项，保持建议项原始顺序 */
    default List<HrmRecruitAgentOptimizeItemDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitAgentOptimizeItemDO>()
                .eq(HrmRecruitAgentOptimizeItemDO::getTaskId, taskId)
                .orderByAsc(HrmRecruitAgentOptimizeItemDO::getSeq));
    }

    /** 任务是否已经写入过确认结果，用于幂等兜底 */
    default boolean existsByTaskId(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<HrmRecruitAgentOptimizeItemDO>()
                .eq(HrmRecruitAgentOptimizeItemDO::getTaskId, taskId)) > 0;
    }
}
