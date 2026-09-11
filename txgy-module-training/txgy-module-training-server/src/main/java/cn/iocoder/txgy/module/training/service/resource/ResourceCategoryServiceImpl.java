package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
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
 * <p>文档 / 视频 / 试题三类分类各自存储一份数据，通过 {@code code} 保持同步：
 * 对任一类型的增删改会联动更新另外两类，做到「改一处、三处同步」。
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
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(CategorySaveReqVO reqVO) {
        checkType(reqVO.getType());
        checkNameUnique(reqVO.getType(), reqVO.getName(), null);

        // code 用于跨类型同步；未传入时（页面新增）自动生成
        String code = StrUtil.isNotBlank(reqVO.getCode()) ? reqVO.getCode() : generateCode();
        Long sourceParentId = ObjUtil.defaultIfNull(reqVO.getParentId(), 0L);
        String parentCode = resolveParentCode(sourceParentId, reqVO.getType());

        Long resultId = null;
        for (String type : ResourceConstants.CATEGORY_TYPES) {
            Long parentId = mapParentId(type, parentCode);
            checkParent(parentId, type);

            ResourceCategoryDO categoryDO = new ResourceCategoryDO();
            categoryDO.setType(type);
            categoryDO.setName(reqVO.getName());
            categoryDO.setCode(code);
            categoryDO.setParentId(parentId);
            categoryDO.setSort(ObjectUtil.defaultIfNull(reqVO.getSort(), 0));
            categoryMapper.insert(categoryDO);

            if (type.equals(reqVO.getType())) {
                resultId = categoryDO.getId();
            }
            operationLogService.createLog(ResourceConstants.ACTION_CREATE, ResourceConstants.TARGET_CATEGORY,
                    categoryDO.getId(), "新增分类：[" + type + "] " + reqVO.getName());
        }
        return resultId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategorySaveReqVO reqVO) {
        ResourceCategoryDO source = validateExists(reqVO.getId());
        checkType(reqVO.getType());
        String code = source.getCode();

        if (StrUtil.isNotBlank(reqVO.getName())) {
            checkNameUnique(reqVO.getType(), reqVO.getName(), reqVO.getId());
        }

        String parentCode = null;
        if (reqVO.getParentId() != null) {
            parentCode = resolveParentCode(reqVO.getParentId(), reqVO.getType());
        }

        for (String type : ResourceConstants.CATEGORY_TYPES) {
            ResourceCategoryDO target = categoryMapper.selectByTypeAndCode(type, code);
            if (target == null) {
                continue;
            }

            ResourceCategoryDO update = new ResourceCategoryDO();
            update.setId(target.getId());
            if (StrUtil.isNotBlank(reqVO.getName())) {
                update.setName(reqVO.getName());
            }
            if (reqVO.getSort() != null) {
                update.setSort(reqVO.getSort());
            }
            if (reqVO.getParentId() != null) {
                update.setParentId(mapParentId(type, parentCode));
                checkParent(update.getParentId(), type);
            }
            categoryMapper.updateById(update);
            operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_CATEGORY,
                    target.getId(), "更新分类：[" + type + "] " + (reqVO.getName() != null ? reqVO.getName() : target.getName()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        ResourceCategoryDO source = validateExists(id);
        String code = source.getCode();

        for (String type : ResourceConstants.CATEGORY_TYPES) {
            ResourceCategoryDO target = categoryMapper.selectByTypeAndCode(type, code);
            if (target == null) {
                continue;
            }
            // 1. 有子分类禁止删除
            if (CollUtil.isNotEmpty(categoryMapper.selectListByParentId(target.getId()))) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_HAS_CHILDREN);
            }
            // 2. 对应类型下存在资源禁止删除
            long count = countResource(type, target.getId());
            if (count > 0) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_HAS_RESOURCE);
            }
            // 3. 逻辑删除
            categoryMapper.deleteById(target.getId());
            operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_CATEGORY,
                    target.getId(), "删除分类：[" + type + "] " + target.getName());
        }
    }

    @Override
    public List<CategoryTreeRespVO> getCategoryTree(String type) {
        checkType(type);
        List<ResourceCategoryDO> list = categoryMapper.selectListByType(type);
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        // 组装树（依赖 selectListByType 按 parent_id 排序，父节点先出现）
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
        fillLevel(roots, 1);
        return roots;
    }

    @Override
    public void validateCategory(Long categoryId, String type) {
        if (categoryId == null || categoryId <= 0) {
            return; // 未选分类允许
        }
        ResourceCategoryDO category = categoryMapper.selectById(categoryId);
        if (category == null || !StrUtil.equals(category.getType(), type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 根据源类型中的父级 ID，解析出父级 code，用于映射到其它类型中的等价父级
     */
    private String resolveParentCode(Long parentId, String type) {
        Long parent = ObjUtil.defaultIfNull(parentId, 0L);
        if (parent == 0L) {
            return null;
        }
        ResourceCategoryDO parentCategory = categoryMapper.selectById(parent);
        if (parentCategory == null || !StrUtil.equals(parentCategory.getType(), type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        return parentCategory.getCode();
    }

    /**
     * 将父级 code 映射为指定类型中的父级 ID；code 为空则映射为根（0）
     */
    private Long mapParentId(String targetType, String parentCode) {
        if (parentCode == null) {
            return 0L;
        }
        ResourceCategoryDO targetParent = categoryMapper.selectByTypeAndCode(targetType, parentCode);
        if (targetParent == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        return targetParent.getId();
    }

    private void checkType(String type) {
        if (!ResourceConstants.CATEGORY_TYPES.contains(type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_TYPE_INVALID);
        }
    }

    /**
     * 父级层级校验：最多支持三级
     */
    private void checkParent(Long parentId, String type) {
        Long parent = ObjUtil.defaultIfNull(parentId, 0L);
        if (parent == 0L) {
            return;
        }
        ResourceCategoryDO parentCategory = categoryMapper.selectById(parent);
        if (parentCategory == null || !StrUtil.equals(parentCategory.getType(), type)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_NOT_EXISTS);
        }
        // 父级是根：可作为二级分类的父级
        if (ObjUtil.equal(parentCategory.getParentId(), 0L)) {
            return;
        }
        // 父级的父级是根：可作为三级分类的父级
        ResourceCategoryDO grandparent = categoryMapper.selectById(parentCategory.getParentId());
        if (grandparent != null && ObjUtil.equal(grandparent.getParentId(), 0L)) {
            return;
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_CATEGORY_PARENT_INVALID);
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

    private String generateCode() {
        return "RC-" + IdUtil.fastSimpleUUID();
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
        vo.setCode(node.getCode());
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
            for (CategoryTreeRespVO child : root.getChildren()) {
                if (ObjUtil.equal(child.getId(), parent.getId())) {
                    return child;
                }
            }
        }
        return null;
    }

    private void fillLevel(List<CategoryTreeRespVO> nodes, int level) {
        for (CategoryTreeRespVO node : nodes) {
            node.setLevel(level);
            fillLevel(node.getChildren(), level + 1);
        }
    }

}
