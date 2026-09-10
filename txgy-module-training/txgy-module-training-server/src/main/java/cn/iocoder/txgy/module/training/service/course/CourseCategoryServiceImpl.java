package cn.iocoder.txgy.module.training.service.course;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryRespVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategorySaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseCategoryMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 课程分类 Service 实现（21 课程创建与维护：三级分类 域/组/课）
 *
 * 与相关子模块的关联：
 *  - 模块二课程管理：课程(training_course)通过 category_id 挂接课程级(第 3 级)分类；
 *  - 模块一资源中心/模块三绑课：通过 code 与 level 定位父域/组进行打标、按域筛选课程；
 *  - 模块四学时统计 / 模块九学时完成率报表：读取课程级分类的 std_hours（标准学时）与 is_required（是否必修）。
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class CourseCategoryServiceImpl implements CourseCategoryService {

    /** 课程分类最大层级：1 域 / 2 组 / 3 课 */
    private static final int MAX_LEVEL = 3;

    @Resource
    private CourseCategoryMapper courseCategoryMapper;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public Long createCategory(CourseCategorySaveReqVO createReqVO) {
        Long parentId = resolveParentId(createReqVO.getParentId());
        validateParent(parentId, null);
        validateNameUnique(null, parentId, createReqVO.getName());
        CourseCategoryDO category = BeanUtils.toBean(createReqVO, CourseCategoryDO.class);
        // 层级由父级自动推算；状态默认 active；父级默认为顶级域
        category.setParentId(parentId);
        category.setLevel(resolveLevel(parentId));
        if (StrUtil.isBlank(category.getStatus())) {
            category.setStatus("active");
        }
        category.setIsRequired(category.getIsRequired() == null ? Boolean.FALSE : category.getIsRequired());
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        courseCategoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategory(CourseCategorySaveReqVO updateReqVO) {
        CourseCategoryDO category = validateCategoryExists(updateReqVO.getId());
        Long parentId = resolveParentId(updateReqVO.getParentId());
        validateParent(parentId, updateReqVO.getId());
        validateNameUnique(category.getId(), parentId, updateReqVO.getName());
        CourseCategoryDO updateObj = BeanUtils.toBean(updateReqVO, CourseCategoryDO.class);
        updateObj.setParentId(parentId);
        updateObj.setLevel(resolveLevel(parentId));
        if (StrUtil.isBlank(updateObj.getStatus())) {
            updateObj.setStatus("active");
        }
        courseCategoryMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        CourseCategoryDO category = validateCategoryExists(id);
        // 删除保护一：存在子分类时禁止删除（需先清理子分类）
        Long childrenCount = courseCategoryMapper.selectChildrenCount(id);
        if (childrenCount != null && childrenCount > 0) {
            throw exception(COURSE_CATEGORY_HAS_CHILDREN);
        }
        // 删除保护二：分类下仍存在课程时禁止删除（与资源中心 categories 约束保持一致，409）
        Long courseCount = courseMapper.selectCountByCategoryId(id);
        if (courseCount != null && courseCount > 0) {
            throw exception(COURSE_CATEGORY_HAS_COURSE);
        }
        courseCategoryMapper.deleteById(category.getId());
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
    public List<CourseCategoryRespVO> getCategoryTree() {
        List<CourseCategoryDO> all = getCategoryList();
        List<CourseCategoryRespVO> vos = BeanUtils.toBean(all, CourseCategoryRespVO.class);
        // 组装 children，仅保留 level=1 的顶级域作为根
        List<CourseCategoryRespVO> roots = new ArrayList<>();
        for (CourseCategoryRespVO vo : vos) {
            Long pid = vo.getParentId() == null ? 0L : vo.getParentId();
            if (pid == 0L) {
                roots.add(vo);
            } else {
                // 父级一定排在前面（列表按 sort/id 升序，父先子后），线性补挂
                for (CourseCategoryRespVO candidate : vos) {
                    if (candidate.getId().equals(pid)) {
                        if (candidate.getChildren() == null) {
                            candidate.setChildren(new ArrayList<>());
                        }
                        candidate.getChildren().add(vo);
                        break;
                    }
                }
            }
        }
        return roots;
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
     * 校验父级分类：必须存在，且父级层级不能为第 3 级（最多三级）
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw exception(COURSE_CATEGORY_PARENT_NOT_EXISTS);
        }
        CourseCategoryDO parent = courseCategoryMapper.selectById(parentId);
        if (parent == null) {
            throw exception(COURSE_CATEGORY_PARENT_NOT_EXISTS);
        }
        if (parent.getLevel() != null && parent.getLevel() >= MAX_LEVEL) {
            throw exception(COURSE_CATEGORY_LEVEL_MAX);
        }
    }

    /**
     * 名称唯一校验：同一父级下名称不重复（排除自身；跨父级允许同名）
     */
    private void validateNameUnique(Long excludeId, Long parentId, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        CourseCategoryDO same = courseCategoryMapper.selectOne(new LambdaQueryWrapperX<CourseCategoryDO>()
                .eq(CourseCategoryDO::getParentId, parentId == null ? 0L : parentId)
                .eq(CourseCategoryDO::getName, name)
                .last("limit 1"));
        if (same != null && (excludeId == null || !same.getId().equals(excludeId))) {
            throw exception(COURSE_CATEGORY_NAME_EXISTS);
        }
    }

    /**
     * 根据父级推算当前层级：无父级=1 级域；否则 = 父级层级 + 1
     */
    private Integer resolveLevel(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }
        CourseCategoryDO parent = courseCategoryMapper.selectById(parentId);
        int level = (parent == null || parent.getLevel() == null) ? 1 : parent.getLevel() + 1;
        return Math.min(level, MAX_LEVEL);
    }

    /**
     * 父级 ID 归一化：null 视为 0（顶级）
     */
    private Long resolveParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

}
