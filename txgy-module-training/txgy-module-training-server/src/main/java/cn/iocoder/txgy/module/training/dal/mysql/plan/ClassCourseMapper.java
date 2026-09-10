package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassCourseDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ClassCourseMapper extends BaseMapperX<ClassCourseDO> {

    default List<ClassCourseDO> selectListByClassId(Long classId) {
        return selectList(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId)
                .orderByAsc(ClassCourseDO::getSort).orderByAsc(ClassCourseDO::getId));
    }

    default ClassCourseDO selectByClassIdAndCourseId(Long classId, Long courseId) {
        return selectOne(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId)
                .eq(ClassCourseDO::getCourseId, courseId)
                .last("limit 1"));
    }

    default Long selectCountByClassId(Long classId) {
        return selectCount(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId));
    }

    default void deleteByClassId(Long classId) {
        delete(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId));
    }

}
