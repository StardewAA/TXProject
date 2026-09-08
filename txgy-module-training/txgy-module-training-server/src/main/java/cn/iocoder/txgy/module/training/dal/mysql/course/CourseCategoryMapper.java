package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程分类 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface CourseCategoryMapper extends BaseMapperX<CourseCategoryDO> {

    default PageResult<CourseCategoryDO> selectPage(CourseCategoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CourseCategoryDO>()
                .eqIfPresent(CourseCategoryDO::getParentId, reqVO.getParentId())
                .eqIfPresent(CourseCategoryDO::getLevel, reqVO.getLevel())
                .eqIfPresent(CourseCategoryDO::getStatus, reqVO.getStatus())
                .likeIfPresent(CourseCategoryDO::getName, reqVO.getName())
                .eqIfPresent(CourseCategoryDO::getCode, reqVO.getCode())
                .orderByAsc(CourseCategoryDO::getSort)
                .orderByDesc(CourseCategoryDO::getId));
    }

    default Long selectChildrenCount(Long parentId) {
        return selectCount(new LambdaQueryWrapperX<CourseCategoryDO>()
                .eq(CourseCategoryDO::getParentId, parentId));
    }

}
