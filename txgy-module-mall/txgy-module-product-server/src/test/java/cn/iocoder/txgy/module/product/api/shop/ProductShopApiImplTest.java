package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ShopReturnAddressDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ShopReturnAddressMapper;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ProductShopApiImplTest {

    private ProductShopService shopService;
    private ShopReturnAddressMapper returnAddressMapper;
    private ProductEnterpriseMapper enterpriseMapper;
    private ProductShopApiImpl api;

    @BeforeEach
    void setUp() {
        shopService = mock(ProductShopService.class);
        returnAddressMapper = mock(ShopReturnAddressMapper.class);
        enterpriseMapper = mock(ProductEnterpriseMapper.class);
        api = new ProductShopApiImpl();
        ReflectionTestUtils.setField(api, "shopService", shopService);
        ReflectionTestUtils.setField(api, "shopReturnAddressMapper", returnAddressMapper);
        ReflectionTestUtils.setField(api, "productEnterpriseMapper", enterpriseMapper);
    }

    @Test
    void prefersPrivateShopDispatchAddress() {
        when(shopService.getShop(10L)).thenReturn(new ProductShopDO().setId(10L).setEnterpriseId(20L));
        when(returnAddressMapper.selectById(10L)).thenReturn(new ShopReturnAddressDO().setShopId(10L).setAddress("店铺发货地址"));

        assertEquals("店铺发货地址", api.getShop(10L).getCheckedData().getDispatchAddress());
        verifyNoInteractions(enterpriseMapper);
    }

    @Test
    void fallsBackToEnterpriseAddressWithoutShopAddress() {
        when(shopService.getShop(10L)).thenReturn(new ProductShopDO().setId(10L).setEnterpriseId(20L));
        when(enterpriseMapper.selectById(20L)).thenReturn(new ProductEnterpriseDO().setId(20L).setAddress("企业档案地址"));

        assertEquals("企业档案地址", api.getShop(10L).getCheckedData().getDispatchAddress());
    }

    @Test
    void keepsOriginEmptyWhenNeitherAddressExists() {
        when(shopService.getShop(10L)).thenReturn(new ProductShopDO().setId(10L).setEnterpriseId(20L));

        assertNull(api.getShop(10L).getCheckedData().getDispatchAddress());
    }
}
