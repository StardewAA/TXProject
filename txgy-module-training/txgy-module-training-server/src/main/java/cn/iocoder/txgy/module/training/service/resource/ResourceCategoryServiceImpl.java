package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategorySaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategoryTreeRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceCategoryDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.DocumentResourceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceCategoryMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资源分类 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class ResourceCategoryServiceImpl implements ResourceCategoryService {

    @Resource
    private ResourceCategoryMapper categoryMapper;
    @Resource
    private DocumentResourceMapper documentResourceMapper;
    @Resource
    private VideoResourceMapper videoResourceMapper;
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private OperationLogService operationLogService;

    @Override
    public Long createCategory(CategorySaveReqVO reqVO) {
        // 1. 类型合法性
        checkType(reqVO.getType());
        // 2. 父级层级校验：仅支持两级（父分类必须是根分类）
        checkParent(reqVO.getParentId(), reqVO.getType());
        // 3. 名称唯一
        checkNameUnique(reqVO.getType(), reqVO.getName(), null);
        // 4. 创建
        ResourceCategoryDO categoryDO = new ResourceCategoryDO();
        categoryDO.setType(reqVO.getType());
        categoryDO.setName(reqVO.getName());
        categoryDO.setParentId(ObjUtil.defaultIfNull(reqVO.getParentId(), 0L));
        categoryDO.setSort(ObjectUtil.defaultIfNull(reqVO.getSort(), 0));
        categoryMapper.insert(categoryDO);
        operationLogService.createLog(ResourceConstants.ACTION_CREATE, ResourceConstants.TARGET_CATEGORY,
                categoryDO.getId(), "新增分类：[" + reqVO.getType() + "] " + reqVO.getName());
        return categoryDO.getId();
    }

    @Override
    public void updateCategory(CategorySaveReqVO reqVO) {
        ResourceCategoryDO old = validateExists(reqVO.getId());
        checkType(reqVO.getType());
        // 父级不能是自己或自己的子级（二级体系下简单校验父级为根）
        if (ObjUtil.notEqual(reqVO.getParentId(), old.getParentId())) {
            checkParent(reqVO.getParentId(), reqVO.getType());
        }
        if (StrUtil.isNotBlank(reqVO.getName())) {
            checkNameUnique(reqVO.getType(), reqVO.getName(), reqVO.getId());
        }
        ResourceCategoryDO update = new ResourceCategoryDO();
        update.setId(reqVO.getId());
        update.setType(reqVO.getType());
        update.setName(reqVO.getName());
        update.setParentId(ObjUtil.defaultIfNull(reqVO.getParentId(), old.getParentId()));
        update.setSort(ObjUtil.defaultIfNull(reqVO.getSort(), old.getSort()));
        categoryMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_CATEGORY,
                reqVO.getId(), "更新分类：" + reqVO.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        ResourceCategoryDO category = validateExists(id);
        // 1. 有子分类禁止删除
        if (CollUtil.isNotEmpty(categoryMapper.selectListByParentId(id))) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_HAS_CHILDREN);
        }
        // 2. 对应类型下存在资源禁止删除
        long count = countResource(category.getType(), id);
        if (count > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_HAS_RESOURCE);
        }
        // 3. 逻辑删除
        categoryMapper.deleteById(id);
        operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_CATEGORY,
                id, "删除分类：" + category.getName());
    }

    @Override
    public List<CategoryTreeRespVO> getCategoryTree(String type) {
        checkType(type);
        List<ResourceCategoryDO> list = categoryMapper.selectListByType(type);
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        // 组装两级树
        Map<Long, ResourceCategoryDO> map = list.stream().collect(Collectors.toMap(ResourceCategoryDO::getId, x -> x));
        List<CategoryTreeRespVO> roots = new ArrayList<>();
        for (ResourceCategoryDO node : list) {
            CategoryTreeRespVO vo = convert(node);
            if (ObjUtil.equal(node.getParentId(), 0L)) {
                roots.add(vo);
            } else {
                CategoryTreeRespVO parent = findTree(map.get(node.getParentId()), roots);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父级缺失（脏数据）按根展示
                    vo.setParentId(0L);
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    @Override
    public void validateCategory(Long categoryId, String type) {
        if (categoryId == null || categoryId <= 0) {
            return; // 未选分类允许
        }
        ResourceCategoryDO category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        if (!StrUtil.equals(category.getType(), type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
    }

    // ========== 私有方法 ==========

    private void checkType(String type) {
        if (!ResourceConstants.CATEGORY_TYPES.contains(type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_TYPE_INVALID);
        }
    }

    private void checkParent(Long parentId, String type) {
        Long parent = ObjUtil.defaultIfNull(parentId, 0L);
        if (parent == 0L) {
            return;
        }
        ResourceCategoryDO parentCategory = categoryMapper.selectById(parent);
        if (parentCategory == null || !StrUtil.equals(parentCategory.getType(), type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        // 只允许一级父分类：父分类必须是根
        if (ObjUtil.notEqual(parentCategory.getParentId(), 0L)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_PARENT_INVALID);
        }
    }

    private void checkNameUnique(String type, String name, Long excludeId) {
        ResourceCategoryDO exist = categoryMapper.selectByNameAndType(type, name);
        if (exist != null && !ObjUtil.equal(exist.getId(), excludeId)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NAME_EXISTS);
        }
    }

    private ResourceCategoryDO validateExists(Long id) {
        ResourceCategoryDO category = categoryMapper.selectById(id);
        if (category == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        return category;
    }

    private long countResource(String type, Long categoryId) {
        if (ResourceConstants.TYPE_DOC.equals(type)) {
            return documentResourceMapper.selectListByCategoryId(categoryId).size();
        }
        if (ResourceConstants.TYPE_VIDEO.equals(type)) {
            return videoResourceMapper.selectListByCategoryId(categoryId).size();
        }
        // 试题暂不绑定分类（按题型+标签打标），故不计入占位分类资源数
        return 0L;
    }

    private CategoryTreeRespVO convert(ResourceCategoryDO node) {
        CategoryTreeRespVO vo = new CategoryTreeRespVO();
        vo.setId(node.getId());
        vo.setName(node.getName());
        vo.setParentId(node.getParentId());
        vo.setSort(node.getSort());
        return vo;
    }

    private CategoryTreeRespVO findTree(ResourceCategoryDO parent, List<CategoryTreeRespVO> roots) {
        if (parent == null) {
            return null;
        }
        for (CategoryTreeRespVO root : roots) {
            if (ObjUtil.equal(root.getId(), parent.getId())) {
                return root;
            }
        }
        return null;
    }

}
