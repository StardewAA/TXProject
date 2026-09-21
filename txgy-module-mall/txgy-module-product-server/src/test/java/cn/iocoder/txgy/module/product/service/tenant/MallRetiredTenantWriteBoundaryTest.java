package cn.iocoder.txgy.module.product.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.txgy.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO;
import cn.iocoder.txgy.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.property.vo.property.ProductPropertySaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.property.vo.value.ProductPropertyValueSaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.txgy.module.product.controller.app.enterprise.EnterpriseApplyReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteSaveReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO;
import cn.iocoder.txgy.module.product.controller.app.supplier.SupplierProductSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.service.brand.ProductBrandServiceImpl;
import cn.iocoder.txgy.module.product.service.category.ProductCategoryServiceImpl;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductEnterpriseServiceImpl;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryService;
import cn.iocoder.txgy.module.product.service.property.ProductPropertyServiceImpl;
import cn.iocoder.txgy.module.product.service.property.ProductPropertyValueServiceImpl;
import cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopServiceImpl;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuServiceImpl;
import cn.iocoder.txgy.module.product.service.supplier.SupplierProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.RETIRED_MALL_TENANT_WRITE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MallRetiredTenantWriteBoundaryTest {

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(3000L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void blocksProductAndEnterpriseWritesBeforeDependenciesAreUsed() {
        assertBlocked(() -> new ProductEnterpriseServiceImpl().create(new ProductEnterpriseDO()));
        assertBlocked(() -> new ProductShopServiceImpl().createShop(new ProductShopDO()));
        assertBlocked(() -> new ProductSpuServiceImpl().createSpu(new ProductSpuSaveReqVO()));
        assertBlocked(() -> new SupplierProductService().save(1L, new SupplierProductSaveReqVO()));
        assertBlocked(() -> new ProductSpuReviewService().submit(1L, 1L, 1L, 1L));
    }

    @Test
    void blocksEnterpriseMembershipAndInquiryWritesBeforeDependenciesAreUsed() {
        assertBlocked(() -> new EnterpriseMembershipService().apply(1L, new EnterpriseApplyReqVO()));
        assertBlocked(() -> new PurchaseInquiryService().save(1L, new InquirySaveReqVO()));
        assertBlocked(() -> new PurchaseInquiryMarketService().saveQuote(1L, new InquiryQuoteSaveReqVO()));
    }

    @Test
    void blocksBrandCategoryAndPropertyDictionaryWritesBeforeDependenciesAreUsed() {
        ProductBrandServiceImpl brands = new ProductBrandServiceImpl();
        assertBlocked(() -> brands.createBrand(new ProductBrandCreateReqVO()));
        assertBlocked(() -> brands.updateBrand(new ProductBrandUpdateReqVO()));
        assertBlocked(() -> brands.deleteBrand(1L));

        ProductCategoryServiceImpl categories = new ProductCategoryServiceImpl();
        assertBlocked(() -> categories.createCategory(new ProductCategorySaveReqVO()));
        assertBlocked(() -> categories.updateCategory(new ProductCategorySaveReqVO()));
        assertBlocked(() -> categories.deleteCategory(1L));

        ProductPropertyServiceImpl properties = new ProductPropertyServiceImpl();
        assertBlocked(() -> properties.createProperty(new ProductPropertySaveReqVO()));
        assertBlocked(() -> properties.updateProperty(new ProductPropertySaveReqVO()));
        assertBlocked(() -> properties.deleteProperty(1L));

        ProductPropertyValueServiceImpl values = new ProductPropertyValueServiceImpl();
        assertBlocked(() -> values.createPropertyValue(new ProductPropertyValueSaveReqVO()));
        assertBlocked(() -> values.updatePropertyValue(new ProductPropertyValueSaveReqVO()));
        assertBlocked(() -> values.deletePropertyValue(1L));
        assertBlocked(() -> values.deletePropertyValueByPropertyId(1L));
    }

    private static void assertBlocked(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(RETIRED_MALL_TENANT_WRITE_FORBIDDEN.getCode(), error.getCode());
    }
}
