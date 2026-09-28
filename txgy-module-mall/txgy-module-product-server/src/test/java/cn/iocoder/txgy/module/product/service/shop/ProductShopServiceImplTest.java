package cn.iocoder.txgy.module.product.service.shop;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.service.enterprise.ProductEnterpriseService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductShopServiceImplTest {

    @Mock private ProductShopMapper shopMapper;
    @Mock private ProductEnterpriseService enterpriseService;
    @InjectMocks private ProductShopServiceImpl service;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void updateShopLocksRowAndNeverAllowsEnterpriseTransfer() {
        ProductShopDO existing = new ProductShopDO();
        existing.setId(20L); existing.setEnterpriseId(100L);
        ProductShopDO update = new ProductShopDO();
        update.setId(20L); update.setEnterpriseId(200L);
        when(shopMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThrows(ServiceException.class, () -> service.updateShop(update));

        ArgumentCaptor<Wrapper<ProductShopDO>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(shopMapper).selectOne(query.capture());
        assertTrue(Objects.toString(org.springframework.test.util.ReflectionTestUtils
                .getField(query.getValue(), "lastSql"), "").contains("FOR UPDATE"));
        verify(shopMapper, never()).updateById(any(ProductShopDO.class));
        verifyNoInteractions(enterpriseService);
    }
}
