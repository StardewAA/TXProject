package cn.iocoder.txgy.module.hrm.dal.mysql.agent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmRecruitAgentAnalysisMapper extends BaseMapperX<HrmRecruitAgentAnalysisDO> {

    default HrmRecruitAgentAnalysisDO selectByTaskId(Long taskId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitAgentAnalysisDO>()
                .eq(HrmRecruitAgentAnalysisDO::getTaskId, taskId));
    }

    default List<HrmRecruitAgentAnalysisDO> selectListByResumeVersionId(Long resumeVersionId) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitAgentAnalysisDO>()
                .eq(HrmRecruitAgentAnalysisDO::getResumeVersionId, resumeVersionId)
                .orderByDesc(HrmRecruitAgentAnalysisDO::getId));
    }

    default List<HrmRecruitAgentAnalysisDO> selectListByTaskIds(Collection<Long> taskIds) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitAgentAnalysisDO>()
                .in(HrmRecruitAgentAnalysisDO::getTaskId, taskIds));
    }
}
