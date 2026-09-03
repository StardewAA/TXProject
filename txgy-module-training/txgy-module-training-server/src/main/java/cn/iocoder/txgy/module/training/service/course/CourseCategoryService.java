package cn.iocoder.txgy.module.training.service.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategorySaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;

import java.util.List;

/**
 * 课程分类 Service 接口（21 课程创建与维护：分类维护）
 *
 * @author 云培训学院课题小组
 */
public interface CourseCategoryService {

    Long createCategory(CourseCategorySaveReqVO createReqVO);

    void updateCategory(CourseCategorySaveReqVO updateReqVO);

    void deleteCategory(Long id);

    PageResult<CourseCategoryDO> getCategoryPage(CourseCategoryPageReqVO pageReqVO);

    List<CourseCategoryDO> getCategoryList();

    CourseCategoryDO getCategory(Long id);

}
