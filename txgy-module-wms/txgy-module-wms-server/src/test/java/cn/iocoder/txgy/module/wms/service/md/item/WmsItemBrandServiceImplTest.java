package cn.iocoder.txgy.module.wms.service.md.item;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandPageReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemBrandDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemBrandMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_BRAND_NAME_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(WmsItemBrandServiceImpl.class)
public class WmsItemBrandServiceImplTest extends BaseDbUnitTest {

    @Resource
    private WmsItemBrandServiceImpl brandService;

    @Resource
    private WmsItemBrandMapper brandMapper;

    @MockitoBean
    private WmsItemService itemService;

    @MockitoBean
    private WmsItemCategoryService categoryService;

    @MockitoBean
    private cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService merchantService;

    @Test
    public void testGetItemBrandPage_enrichesCategoryAndSupplierNames() {
        WmsItemBrandDO brand = createBrand("B003", "秦岭包装");
        brand.setCategoryId(2L);
        brand.setSupplierId(3L);
        brandMapper.insert(brand);
        when(categoryService.getItemCategoryMap(any())).thenReturn(Map.of(2L,
                WmsItemCategoryDO.builder().id(2L).name("包装材料").build()));
        when(merchantService.getMerchantMap(any())).thenReturn(Map.of(3L,
                WmsMerchantDO.builder().id(3L).name("秦岭新材包装科技有限公司").build()));

        WmsItemBrandDO result = brandService.getItemBrandPage(new WmsItemBrandPageReqVO())
                .getList().getFirst();

        assertEquals("包装材料", result.getCategoryName());
        assertEquals("秦岭新材包装科技有限公司", result.getSupplierName());
        WmsItemBrandRespVO response = BeanUtils.toBean(result, WmsItemBrandRespVO.class);
        assertEquals("包装材料", response.getCategoryName());
        assertEquals("秦岭新材包装科技有限公司", response.getSupplierName());
    }

    @Test
    public void testGetItemBrandPage_withoutAssociations_keepsNamesEmpty() {
        brandMapper.insert(createBrand("B004", "无关联品牌"));

        WmsItemBrandDO result = brandService.getItemBrandPage(new WmsItemBrandPageReqVO())
                .getList().getFirst();

        assertNull(result.getCategoryName());
        assertNull(result.getSupplierName());
    }

    @Test
    public void testGetItemBrand_missingAssociatedRecords_keepsNamesEmpty() {
        WmsItemBrandDO brand = createBrand("B005", "关联记录已删除");
        brand.setCategoryId(20L);
        brand.setSupplierId(30L);
        brandMapper.insert(brand);
        when(categoryService.getItemCategoryMap(any())).thenReturn(Map.of());
        when(merchantService.getMerchantMap(any())).thenReturn(Map.of());

        WmsItemBrandDO result = brandService.getItemBrand(brand.getId());

        assertNull(result.getCategoryName());
        assertNull(result.getSupplierName());
    }

    @Test
    public void testCreateItemBrand_nameDuplicate() {
        // mock 数据
        brandMapper.insert(createBrand("B001", "华为"));
        WmsItemBrandSaveReqVO reqVO = createBrandSaveReqVO(null, "B002", "华为");

        // 调用，并断言
        assertServiceException(() -> brandService.createItemBrand(reqVO), ITEM_BRAND_NAME_DUPLICATE);
    }

    @Test
    public void testUpdateItemBrand_nameDuplicate() {
        // mock 数据
        brandMapper.insert(createBrand("B001", "华为"));
        WmsItemBrandDO brand = createBrand("B002", "小米");
        brandMapper.insert(brand);
        WmsItemBrandSaveReqVO reqVO = createBrandSaveReqVO(brand.getId(), "B002", "华为");

        // 调用，并断言
        assertServiceException(() -> brandService.updateItemBrand(reqVO), ITEM_BRAND_NAME_DUPLICATE);
    }

    private static WmsItemBrandDO createBrand(String code, String name) {
        return WmsItemBrandDO.builder()
                .code(code)
                .name(name)
                .build();
    }

    private static WmsItemBrandSaveReqVO createBrandSaveReqVO(Long id, String code, String name) {
        WmsItemBrandSaveReqVO reqVO = new WmsItemBrandSaveReqVO();
        reqVO.setId(id);
        reqVO.setCode(code);
        reqVO.setName(name);
        return reqVO;
    }

}
