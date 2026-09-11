package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.util.Collections;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_HAS_ASSOCIATIONS;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_IS_DISABLED;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_STATUS_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@Import(WmsItemServiceImpl.class)
class WmsItemServiceImplTest extends BaseDbUnitTest {

    @Resource
    private WmsItemService itemService;
    @Resource
    private WmsItemMapper itemMapper;

    @MockitoBean
    private WmsItemCategoryService categoryService;
    @MockitoBean
    private WmsItemBrandService brandService;
    @MockitoBean
    private WmsItemSkuService itemSkuService;
    @MockitoBean
    private WmsItemQualityReportService qualityReportService;

    @Test
    void testDeleteItem_withoutAssociations_deletesItem() {
        WmsItemDO item = createItem();
        when(itemSkuService.getItemSkuAssociationSummary(item.getId())).thenReturn(Collections.emptyList());
        when(qualityReportService.getQualityReportCount(item.getId())).thenReturn(0L);

        itemService.deleteItem(item.getId());

        verify(itemSkuService).deleteItemSkuListByItemId(item.getId());
        assertNull(itemMapper.selectById(item.getId()));
    }

    @Test
    void testDeleteItem_withAssociations_rejectsAndKeepsItem() {
        WmsItemDO item = createItem();
        when(itemSkuService.getItemSkuAssociationSummary(item.getId()))
                .thenReturn(Collections.singletonList("库存信息(1条)"));

        assertServiceException(() -> itemService.deleteItem(item.getId()), ITEM_HAS_ASSOCIATIONS,
                item.getName(), "库存信息(1条)");

        verify(itemSkuService, never()).deleteItemSkuListByItemId(item.getId());
        verify(qualityReportService).getQualityReportCount(item.getId());
        assertNotNull(itemMapper.selectById(item.getId()));
    }

    @Test
    void testUpdateItemStatus_updatesStatus() {
        WmsItemDO item = createItem();

        itemService.updateItemStatus(item.getId(), CommonStatusEnum.DISABLE.getStatus());

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), itemMapper.selectById(item.getId()).getStatus());
    }

    @Test
    void testUpdateItemStatus_rejectsInvalidStatus() {
        WmsItemDO item = createItem();

        assertServiceException(() -> itemService.updateItemStatus(item.getId(), 2), ITEM_STATUS_INVALID);
    }

    @Test
    void testValidateItemExistsAndEnabled_rejectsDisabledItem() {
        WmsItemDO item = createItem();
        itemMapper.updateById(new WmsItemDO().setId(item.getId()).setStatus(CommonStatusEnum.DISABLE.getStatus()));

        assertServiceException(() -> itemService.validateItemExistsAndEnabled(item.getId()), ITEM_IS_DISABLED);
    }

    private WmsItemDO createItem() {
        WmsItemDO item = new WmsItemDO().setCode("ITEM-DELETE-" + System.nanoTime())
                .setName("删除测试商品-" + System.nanoTime()).setCategoryId(1L).setUnit("件")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        itemMapper.insert(item);
        return item;
    }
}
