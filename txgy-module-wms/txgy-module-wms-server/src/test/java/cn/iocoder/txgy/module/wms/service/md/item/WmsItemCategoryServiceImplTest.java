package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.category.WmsItemCategorySaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemCategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_CATEGORY_NOT_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(WmsItemCategoryServiceImpl.class)
public class WmsItemCategoryServiceImplTest extends BaseDbUnitTest {

    @Resource
    private WmsItemCategoryServiceImpl categoryService;

    @Resource
    private WmsItemCategoryMapper categoryMapper;

    @MockitoBean
    private WmsItemService itemService;

    @Test
    public void testGetEnabledItemCategoryList_excludesDisabledAncestors() {
        WmsItemCategoryDO enabledRoot = createCategory(0L, "C001", "根分类", CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(enabledRoot);
        WmsItemCategoryDO enabledChild = createCategory(enabledRoot.getId(), "C002", "启用子分类",
                CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(enabledChild);
        WmsItemCategoryDO disabledRoot = createCategory(0L, "C003", "关闭根分类",
                CommonStatusEnum.DISABLE.getStatus());
        categoryMapper.insert(disabledRoot);
        WmsItemCategoryDO childOfDisabledRoot = createCategory(disabledRoot.getId(), "C004", "关闭根分类的启用子分类",
                CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(childOfDisabledRoot);

        List<WmsItemCategoryDO> enabledCategories = categoryService.getEnabledItemCategoryList();
        Set<Long> enabledIds = enabledCategories.stream()
                .map(WmsItemCategoryDO::getId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(enabledRoot.getId(), enabledChild.getId()), enabledIds);
    }

    @Test
    public void testUpdateItemCategory_disable_syncsChildren() {
        WmsItemCategoryDO root = createCategory(0L, "C001", "根分类", CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(root);
        WmsItemCategoryDO child = createCategory(root.getId(), "C002", "子分类", CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(child);
        WmsItemCategoryDO grandchild = createCategory(child.getId(), "C003", "孙分类",
                CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(grandchild);

        categoryService.updateItemCategory(createSaveReqVO(root, CommonStatusEnum.DISABLE.getStatus()));

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), categoryMapper.selectById(root.getId()).getStatus());
        assertEquals(CommonStatusEnum.DISABLE.getStatus(), categoryMapper.selectById(child.getId()).getStatus());
        assertEquals(CommonStatusEnum.DISABLE.getStatus(), categoryMapper.selectById(grandchild.getId()).getStatus());
    }

    @Test
    public void testCreateItemCategory_disabledParent_forcesDisabled() {
        WmsItemCategoryDO parent = createCategory(0L, "C001", "关闭父分类",
                CommonStatusEnum.DISABLE.getStatus());
        categoryMapper.insert(parent);
        WmsItemCategorySaveReqVO reqVO = new WmsItemCategorySaveReqVO();
        reqVO.setParentId(parent.getId());
        reqVO.setCode("C002");
        reqVO.setName("新增子分类");
        reqVO.setSort(0);
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());

        Long childId = categoryService.createItemCategory(reqVO);

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), categoryMapper.selectById(childId).getStatus());
    }

    @Test
    public void testCreateItemCategory_disabledAncestor_forcesDisabled() {
        WmsItemCategoryDO disabledRoot = createCategory(0L, "C001", "关闭祖先分类",
                CommonStatusEnum.DISABLE.getStatus());
        categoryMapper.insert(disabledRoot);
        WmsItemCategoryDO enabledParent = createCategory(disabledRoot.getId(), "C002", "历史启用父分类",
                CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(enabledParent);
        WmsItemCategorySaveReqVO reqVO = new WmsItemCategorySaveReqVO();
        reqVO.setParentId(enabledParent.getId());
        reqVO.setCode("C003");
        reqVO.setName("新增孙分类");
        reqVO.setSort(0);
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());

        Long childId = categoryService.createItemCategory(reqVO);

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), categoryMapper.selectById(childId).getStatus());
    }

    @Test
    public void testValidateItemCategoryEnabled_disabled() {
        WmsItemCategoryDO category = createCategory(0L, "C001", "关闭分类", CommonStatusEnum.DISABLE.getStatus());
        categoryMapper.insert(category);

        assertServiceException(() -> categoryService.validateItemCategoryEnabled(category.getId()),
                ITEM_CATEGORY_NOT_ENABLED);
    }

    @Test
    public void testValidateItemCategoryEnabled_cycleRejected() {
        WmsItemCategoryDO first = createCategory(0L, "C001", "循环分类一", CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(first);
        WmsItemCategoryDO second = createCategory(first.getId(), "C002", "循环分类二",
                CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(second);
        first.setParentId(second.getId());
        categoryMapper.updateById(first);

        assertServiceException(() -> categoryService.validateItemCategoryEnabled(first.getId()),
                ITEM_CATEGORY_NOT_ENABLED);
    }

    private static WmsItemCategoryDO createCategory(Long parentId, String code, String name, Integer status) {
        return WmsItemCategoryDO.builder()
                .parentId(parentId)
                .code(code)
                .name(name)
                .sort(0)
                .status(status)
                .build();
    }

    private static WmsItemCategorySaveReqVO createSaveReqVO(WmsItemCategoryDO category, Integer status) {
        WmsItemCategorySaveReqVO reqVO = new WmsItemCategorySaveReqVO();
        reqVO.setId(category.getId());
        reqVO.setParentId(category.getParentId());
        reqVO.setCode(category.getCode());
        reqVO.setName(category.getName());
        reqVO.setSort(category.getSort());
        reqVO.setStatus(status);
        return reqVO;
    }

}
