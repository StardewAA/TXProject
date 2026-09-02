package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCommentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCommentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseCommentMapper extends BaseMapperX<CourseCommentDO> {

    default PageResult<CourseCommentDO> selectPage(CourseCommentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CourseCommentDO>()
                .eq(CourseCommentDO::getCourseId, reqVO.getCourseId())
                .eqIfPresent(CourseCommentDO::getParentId, reqVO.getParentId())
                .orderByDesc(CourseCommentDO::getId));
    }

}
