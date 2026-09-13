package cn.iocoder.txgy.module.product.service.supplier;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.dal.mysql.sku.*;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.*;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplierStockServiceTest {
    SupplierStockService service; EnterpriseMembershipService auth; ProductSkuMapper skus;
    ProductSpuMapper spus; SupplierStockAdjustmentMapper logs; ProductSkuDO sku; ProductSpuDO spu;
    @BeforeEach void setup(){
        service=new SupplierStockService(); auth=mock(EnterpriseMembershipService.class);
        skus=mock(ProductSkuMapper.class);spus=mock(ProductSpuMapper.class);logs=mock(SupplierStockAdjustmentMapper.class);
        ReflectionTestUtils.setField(service,"membership",auth);ReflectionTestUtils.setField(service,"skus",skus);
        ReflectionTestUtils.setField(service,"spus",spus);ReflectionTestUtils.setField(service,"adjustments",logs);
        sku=new ProductSkuDO().setId(3L).setSpuId(2L).setStock(10).setSalesCount(8);
        spu=new ProductSpuDO().setId(2L).setShopId(1L).setStatus(0).setStock(20).setSalesCount(8);
        when(skus.selectOne(any())).thenReturn(sku);when(spus.selectOne(any())).thenReturn(spu);
    }
    SupplierStockService.Adjust req(int expected,int delta){return new SupplierStockService.Adjust(1L,2L,3L,expected,delta," 本地补货 ","stock-test-123456789");}
    @Test void deniedWithoutAuthority(){doThrow(new ServiceException(403,"无权限")).when(auth).requireShop(9L,1L);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,2)));verifyNoInteractions(skus,spus,logs);}
    @Test void rejectsForeignProduct(){when(spus.selectOne(any())).thenReturn(null);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,2)));verifyNoInteractions(skus,logs);}
    @Test void rejectsForeignSku(){when(skus.selectOne(any())).thenReturn(null);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,2)));verify(logs,never()).insert(any(SupplierStockAdjustmentDO.class));}
    @Test void changesOnlyStocksAndLogs(){var result=service.adjust(9L,req(10,2));assertEquals(12,result.afterStock());
        verify(skus).updateById(argThat((ProductSkuDO s)->s.getStock()==12&&s.getSalesCount()==null&&s.getWmsSkuId()==null&&s.getPrice()==null));
        verify(spus).updateById(argThat((ProductSpuDO s)->s.getStock()==22&&s.getSalesCount()==null&&s.getPrice()==null));
        verify(logs).insert(argThat((SupplierStockAdjustmentDO s)->s.getMemberId()==9L&&s.getBeforeStock()==10&&s.getAfterStock()==12&&s.getReason().equals("本地补货")));}
    @Test void canReduceToZero(){assertEquals(0,service.adjust(9L,req(10,-10)).afterStock());}
    @Test void rejectsStaleStock(){assertThrows(ServiceException.class,()->service.adjust(9L,req(9,2)));verify(skus,never()).updateById(any(ProductSkuDO.class));}
    @Test void rejectsNegativeOverflowAndZero(){assertThrows(ServiceException.class,()->service.adjust(9L,req(10,-11)));assertThrows(ServiceException.class,()->service.adjust(9L,req(10,0)));spu.setStock(Integer.MAX_VALUE);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,1)));verify(skus,never()).updateById(any(ProductSkuDO.class));}
    @Test void rejectsPartialWmsMapping(){sku.setWmsSkuId(99L);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,2)));sku.setWmsSkuId(null).setWmsWarehouseId(99L);assertThrows(ServiceException.class,()->service.adjust(9L,req(10,2)));verify(skus,never()).updateById(any(ProductSkuDO.class));}
    @Test void idempotentReplayAndChangedPayloadRejected(){var row=new SupplierStockAdjustmentDO();row.setShopId(1L);row.setSpuId(2L);row.setSkuId(3L);row.setExpectedStock(10);row.setDelta(2);row.setReason("本地补货");row.setAfterStock(12);when(logs.selectOne(any())).thenReturn(row);sku.setStock(12);
        assertEquals(12,service.adjust(9L,req(10,2)).afterStock());assertThrows(ServiceException.class,()->service.adjust(9L,req(10,3)));verify(skus,never()).updateById(any(ProductSkuDO.class));verify(logs,never()).insert(any(SupplierStockAdjustmentDO.class));}
}
