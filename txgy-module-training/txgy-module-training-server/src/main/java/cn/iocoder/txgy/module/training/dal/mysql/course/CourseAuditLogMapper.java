package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseAuditLogPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseAuditLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseAuditLogMapper extends BaseMapperX<CourseAuditLogDO> {

    default PageResult<CourseAuditLogDO> selectPage(CourseAuditLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CourseAuditLogDO>()
                .eqIfPresent(CourseAuditLogDO::getCourseId, reqVO.getCourseId())
                .eqIfPresent(CourseAuditLogDO::getAction, reqVO.getAction())
                .orderByDesc(CourseAuditLogDO::getId));
    }

    default Long selectCountByCourseId(Long courseId) {
        return selectCount(new LambdaQueryWrapperX<CourseAuditLogDO>()
                .eq(CourseAuditLogDO::getCourseId, courseId));
    }

}
