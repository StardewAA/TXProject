package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CourseMediaMapper extends BaseMapperX<CourseMediaDO> {

    default List<CourseMediaDO> selectListByCourseId(Long courseId) {
        return selectList(new LambdaQueryWrapperX<CourseMediaDO>()
                .eq(CourseMediaDO::getCourseId, courseId)
                .orderByAsc(CourseMediaDO::getSort)
                .orderByAsc(CourseMediaDO::getId));
    }

    default Long selectCountByCourseId(Long courseId) {
        return selectCount(new LambdaQueryWrapperX<CourseMediaDO>()
                .eq(CourseMediaDO::getCourseId, courseId));
    }

    default CourseMediaDO selectByUrl(Long courseId, String url) {
        return selectOne(new LambdaQueryWrapperX<CourseMediaDO>()
                .eq(CourseMediaDO::getCourseId, courseId)
                .eq(CourseMediaDO::getUrl, url)
                .last("limit 1"));
    }

}
