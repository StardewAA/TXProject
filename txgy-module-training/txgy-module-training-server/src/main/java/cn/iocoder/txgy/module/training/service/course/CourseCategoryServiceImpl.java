package cn.iocoder.txgy.module.training.service.course;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategorySaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseCategoryMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 课程分类 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class CourseCategoryServiceImpl implements CourseCategoryService {

    @Resource
    private CourseCategoryMapper courseCategoryMapper;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public Long createCategory(CourseCategorySaveReqVO createReqVO) {
        validateNameUnique(null, createReqVO.getName());
        CourseCategoryDO category = BeanUtils.toBean(createReqVO, CourseCategoryDO.class);
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        courseCategoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategory(CourseCategorySaveReqVO updateReqVO) {
        CourseCategoryDO category = validateCategoryExists(updateReqVO.getId());
        validateNameUnique(category.getId(), updateReqVO.getName());
        CourseCategoryDO updateObj = BeanUtils.toBean(updateReqVO, CourseCategoryDO.class);
        courseCategoryMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        validateCategoryExists(id);
        // 删除保护：分类下仍存在课程时禁止删除（资源中心 categories 同款约束，409）
        Long courseCount = courseMapper.selectCountByCategoryId(id);
        if (courseCount != null && courseCount > 0) {
            throw exception(COURSE_CATEGORY_HAS_COURSE);
        }
        courseCategoryMapper.deleteById(id);
    }

    @Override
    public PageResult<CourseCategoryDO> getCategoryPage(CourseCategoryPageReqVO pageReqVO) {
        return courseCategoryMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CourseCategoryDO> getCategoryList() {
        return courseCategoryMapper.selectList(new LambdaQueryWrapperX<CourseCategoryDO>()
                .orderByAsc(CourseCategoryDO::getSort)
                .orderByAsc(CourseCategoryDO::getId));
    }

    @Override
    public CourseCategoryDO getCategory(Long id) {
        return courseCategoryMapper.selectById(id);
    }

    private CourseCategoryDO validateCategoryExists(Long id) {
        CourseCategoryDO category = courseCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(COURSE_CATEGORY_NOT_EXISTS);
        }
        return category;
    }

    /**
     * 分类名称唯一校验（排除自身）
     */
    private void validateNameUnique(Long excludeId, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        CourseCategoryDO same = courseCategoryMapper.selectOne(new LambdaQueryWrapperX<CourseCategoryDO>()
                .eq(CourseCategoryDO::getName, name)
                .last("limit 1"));
        if (same != null && (excludeId == null || !same.getId().equals(excludeId))) {
            throw exception(COURSE_CATEGORY_NAME_EXISTS);
        }
    }

}
