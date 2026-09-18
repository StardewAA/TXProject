package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学员学习任务 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface LearnerTaskMapper extends BaseMapperX<LearnerTaskDO> {

    default LearnerTaskDO selectByUserIdAndCourseId(Long userId, Long courseId) {
        return selectOne(new LambdaQueryWrapperX<LearnerTaskDO>()
                .eq(LearnerTaskDO::getUserId, userId)
                .eq(LearnerTaskDO::getCourseId, courseId)
                .last("limit 1"));
    }

}
