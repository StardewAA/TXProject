package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StudentTaskMapper extends BaseMapperX<StudentTaskDO> {

    default List<StudentTaskDO> selectListByClassId(Long classId) {
        return selectList(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId));
    }

    default List<StudentTaskDO> selectListByClassIdAndUserIds(Long classId, Collection<Long> userIds) {
        return selectList(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .inIfPresent(StudentTaskDO::getUserId, userIds));
    }

    default StudentTaskDO selectByClassUserCourse(Long classId, Long userId, Long courseId) {
        return selectOne(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .eq(StudentTaskDO::getUserId, userId)
                .eq(StudentTaskDO::getCourseId, courseId)
                .last("limit 1"));
    }

    default void deleteByClassIdAndUserIds(Long classId, Collection<Long> userIds) {
        delete(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .inIfPresent(StudentTaskDO::getUserId, userIds));
    }

    default Long selectCountUnfinishedByClassId(Long classId) {
        return selectCount(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .ne(StudentTaskDO::getStatus, 2));
    }

}
