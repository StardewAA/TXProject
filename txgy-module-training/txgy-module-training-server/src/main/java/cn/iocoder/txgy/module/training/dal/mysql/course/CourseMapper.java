package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import org.apache.ibatis.annotations.Mapper;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

@Mapper
public interface CourseMapper extends BaseMapperX<CourseDO> {

    default PageResult<CourseDO> selectPage(CoursePageReqVO reqVO) {
        LambdaQueryWrapperX<CourseDO> query = new LambdaQueryWrapperX<CourseDO>()
                .likeIfPresent(CourseDO::getTitle, reqVO.getTitle())
                .likeIfPresent(CourseDO::getCode, reqVO.getCode())
                .eqIfPresent(CourseDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CourseDO::getLecturer, reqVO.getLecturer())
                .eqIfPresent(CourseDO::getCourseLevel, reqVO.getCourseLevel())
                .eqIfPresent(CourseDO::getIsRequired, reqVO.getIsRequired())
                .eqIfPresent(CourseDO::getIsComposite, reqVO.getIsComposite())
                .eqIfPresent(CourseDO::getIsCurrent, reqVO.getIsCurrent());
        if (CollUtil.isNotEmpty(reqVO.getCategoryIds())) {
            query.in(CourseDO::getCategoryId, reqVO.getCategoryIds());
        } else {
            query.eqIfPresent(CourseDO::getCategoryId, reqVO.getCategoryId());
        }
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(item -> item.like(CourseDO::getTitle, reqVO.getKeyword())
                    .or().like(CourseDO::getCode, reqVO.getKeyword())
                    .or().like(CourseDO::getLecturer, reqVO.getKeyword()));
        }
        boolean ascending = "asc".equalsIgnoreCase(reqVO.getSortOrder());
        if ("latest".equals(reqVO.getSortField())) {
            query.orderBy(true, ascending, CourseDO::getCreateTime);
        } else if ("credit".equals(reqVO.getSortField())) {
            query.orderBy(true, ascending, CourseDO::getCredit);
        } else {
            query.orderByDesc(CourseDO::getId);
        }
        return selectPage(reqVO, query);
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

    /**
     * 查询某分类下未被删除的课程数量（分类删除保护校验）
     */
    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(new LambdaQueryWrapperX<CourseDO>()
                .eq(CourseDO::getCategoryId, categoryId));
    }

    /**
     * 查询标记为某成长等级的课程数量（成长等级删除保护校验）
     */
    default Long selectCountByCourseLevel(String courseLevel) {
        return selectCount(new LambdaQueryWrapperX<CourseDO>()
                .eq(CourseDO::getCourseLevel, courseLevel));
    }

}
