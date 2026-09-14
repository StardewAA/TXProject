package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.WmsItemSkuController;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.sku.WmsItemSkuPageReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WmsItemSkuMaterialResponseTest {
    @Test void exposesActualMaterialTypeAlongsideSku() {
        var controller=new WmsItemSkuController();
        var skus=mock(WmsItemSkuService.class); var items=mock(WmsItemService.class);
        ReflectionTestUtils.setField(controller,"itemSkuService",skus);
        ReflectionTestUtils.setField(controller,"itemService",items);
        ReflectionTestUtils.setField(controller,"categoryService",mock(WmsItemCategoryService.class));
        ReflectionTestUtils.setField(controller,"brandService",mock(WmsItemBrandService.class));
        when(skus.getItemSkuPage(any())).thenReturn(new PageResult<>(List.of(WmsItemSkuDO.builder().id(11L).itemId(1L).build()),1L));
        when(items.getItemMap(any())).thenReturn(Map.of(1L,WmsItemDO.builder().id(1L).name("不能凭名称猜类型").materialType("RAW_MATERIAL").build()));
        String json=JsonUtils.toJsonString(controller.getItemSkuPage(new WmsItemSkuPageReqVO()).getCheckedData());
        assertTrue(json.contains("\"materialType\":\"RAW_MATERIAL\""),json);
    }
}
