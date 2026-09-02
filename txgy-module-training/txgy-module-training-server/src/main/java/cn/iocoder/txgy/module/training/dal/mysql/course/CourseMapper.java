package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapperX<CourseDO> {

    default PageResult<CourseDO> selectPage(CoursePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CourseDO>()
                .likeIfPresent(CourseDO::getTitle, reqVO.getTitle())
                .likeIfPresent(CourseDO::getCode, reqVO.getCode())
                .eqIfPresent(CourseDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(CourseDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CourseDO::getLecturer, reqVO.getLecturer())
                .eqIfPresent(CourseDO::getIsCurrent, reqVO.getIsCurrent())
                .orderByDesc(CourseDO::getId));
    }

    /**
     * 查询当前生效版本课程（is_current = true）
     */
    default CourseDO selectCurrentByRootId(Long rootId) {
        return selectOne(new LambdaQueryWrapperX<CourseDO>()
                .eq(CourseDO::getRootId, rootId)
                .eq(CourseDO::getIsCurrent, true)
                .last("limit 1"));
    }

}
