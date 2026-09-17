package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductReviewWriteSafetyTest {
    ProductSkuServiceImpl service; ProductSkuMapper mapper; ProductSkuDO existing;
    @BeforeEach void setup() {
        service=new ProductSkuServiceImpl();mapper=mock(ProductSkuMapper.class);
        ReflectionTestUtils.setField(service,"productSkuMapper",mapper);
        existing=new ProductSkuDO().setId(100L).setSpuId(10L).setStock(12).setSalesCount(8).setPrice(200)
                .setWmsSkuId(555L).setWmsWarehouseId(666L)
                .setProperties(List.of(new ProductSkuDO.Property(1L,"厚度",2L,"50mm")));
        when(mapper.selectListBySpuId(10L)).thenReturn(List.of(existing));
        when(mapper.selectList()).thenReturn(List.of(existing));
        when(mapper.updateById(any(ProductSkuDO.class))).thenReturn(1);
    }
    ProductSkuSaveReqVO input() {
        return new ProductSkuSaveReqVO().setPrice(201).setStock(9999).setWmsSkuId(555L).setWmsWarehouseId(666L)
                .setProperties(List.of(new ProductSkuSaveReqVO.Property(1L,"厚度",2L,"50mm")));
    }
    @Test void patchPreservesActualSkuIdentityAndNeverWritesDynamicInventoryOrMapping() {
        service.updateSkuList(10L,List.of(input()));
        var capture=ArgumentCaptor.forClass(ProductSkuDO.class);verify(mapper).updateById(capture.capture());
        var patch=capture.getValue();assertEquals(100L,patch.getId());assertEquals(201,patch.getPrice());
        assertNull(patch.getStock());assertNull(patch.getSalesCount());assertNull(patch.getWmsSkuId());
        assertNull(patch.getWmsWarehouseId());assertNull(patch.getProperties());
    }
    @Test void changedMappingRejectedWithoutAnySkuWrite() {
        assertThrows(ServiceException.class,()->service.updateSkuList(10L,List.of(input().setWmsSkuId(999L))));
        verify(mapper,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void changedSpecOrMissingSkuDoesNotDeleteOrRecreateHistory() {
        var changed=input().setProperties(List.of(new ProductSkuSaveReqVO.Property(1L,"厚度",3L,"60mm")));
        assertThrows(ServiceException.class,()->service.updateSkuList(10L,List.of(changed)));
        assertThrows(ServiceException.class,()->service.updateSkuList(10L,List.of()));
        verify(mapper,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void usedPropertyRenameDoesNotMutateExistingSkuSnapshot() {
        assertThrows(ServiceException.class,()->service.updateSkuProperty(1L,"新厚度"));
        assertThrows(ServiceException.class,()->service.updateSkuPropertyValue(2L,"60mm"));
        assertEquals("厚度",existing.getProperties().getFirst().getPropertyName());
        assertEquals("50mm",existing.getProperties().getFirst().getValueName());
        assertEquals(12,existing.getStock()); verify(mapper,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void noOpPropertyRenameDoesNotWriteAnySku() {
        assertEquals(0,service.updateSkuProperty(1L,"厚度"));assertEquals(0,service.updateSkuPropertyValue(2L,"50mm"));
        verify(mapper,never()).updateById(any(ProductSkuDO.class));
    }
    @Test void createAndCopyCannotInheritWmsMappingBeforeAnyInsert() {
        assertThrows(ServiceException.class,()->service.createSkuList(20L,List.of(input())));
        assertThrows(ServiceException.class,()->ProductSkuServiceImpl.validateNewSkuMappings(List.of(input().setWmsSkuId(null))));
        assertThrows(ServiceException.class,()->ProductSkuServiceImpl.validateNewSkuMappings(List.of(input().setWmsWarehouseId(null))));
        verifyNoInteractions(mapper);
    }
    @Test void unmappedCreationKeepsExistingInitialStockRule() {
        var create=input().setWmsSkuId(null).setWmsWarehouseId(null).setStock(15);
        assertDoesNotThrow(()->ProductSkuServiceImpl.validateNewSkuMappings(List.of(create)));
        assertEquals(15,create.getStock()); verifyNoInteractions(mapper);
    }
}
