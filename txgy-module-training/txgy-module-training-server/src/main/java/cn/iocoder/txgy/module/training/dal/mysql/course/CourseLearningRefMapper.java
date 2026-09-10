package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseLearningRefDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程学习引用占位 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface CourseLearningRefMapper extends BaseMapperX<CourseLearningRefDO> {

    default Long selectCountByCourseId(Long courseId) {
        return selectCount(new LambdaQueryWrapperX<CourseLearningRefDO>()
                .eq(CourseLearningRefDO::getCourseId, courseId));
    }

}
