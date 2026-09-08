package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学习会话 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface LearnSessionMapper extends BaseMapperX<LearnSessionDO> {

    default List<LearnSessionDO> selectActiveListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getUserId, userId)
                .eq(LearnSessionDO::getStatus, "active")
                .orderByDesc(LearnSessionDO::getId));
    }

    default List<LearnSessionDO> selectListByUserAndCourse(Long userId, Long courseId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getUserId, userId)
                .eq(LearnSessionDO::getCourseId, courseId));
    }

    default List<LearnSessionDO> selectEndedListByUserAndCourse(Long userId, Long courseId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getUserId, userId)
                .eq(LearnSessionDO::getCourseId, courseId)
                .eq(LearnSessionDO::getStatus, "ended")
                .orderByAsc(LearnSessionDO::getMediaId)
                .orderByAsc(LearnSessionDO::getStartAt));
    }

    default List<LearnSessionDO> selectEndedListByCourseId(Long courseId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getCourseId, courseId)
                .eq(LearnSessionDO::getStatus, "ended")
                .orderByAsc(LearnSessionDO::getMediaId)
                .orderByAsc(LearnSessionDO::getStartAt));
    }

    default List<LearnSessionDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getUserId, userId)
                .orderByDesc(LearnSessionDO::getId));
    }

    default List<LearnSessionDO> selectEndedListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<LearnSessionDO>()
                .eq(LearnSessionDO::getUserId, userId)
                .eq(LearnSessionDO::getStatus, "ended")
                .orderByDesc(LearnSessionDO::getId));
    }

}
