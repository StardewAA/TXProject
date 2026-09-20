package cn.iocoder.txgy.module.product.service.wms;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuServiceImpl;
import cn.iocoder.txgy.module.product.service.sku.adapter.ProductWmsInventoryDatabaseAdapter;
import cn.iocoder.txgy.module.product.service.wms.readonly.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WmsReadonlyBoundaryTest {
    JdbcTemplate jdbc;ProductSkuServiceImpl service;ProductWmsReadonlyQueryAdapter adapter;
    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:readonly-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE product_spu(id BIGINT,tenant_id BIGINT,status INT,deleted INT)");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT,tenant_id BIGINT,spu_id BIGINT,wms_sku_id BIGINT,deleted INT)");
        jdbc.update("INSERT INTO product_spu VALUES(1,1,1,0),(2,1,0,0),(3,2,1,0)");
        jdbc.update("INSERT INTO product_sku VALUES(11,1,1,101,0),(12,1,2,102,0),(13,2,3,103,0),(14,1,1,104,1)");
        service=new ProductSkuServiceImpl();adapter=mock(ProductWmsReadonlyQueryAdapter.class);
        ReflectionTestUtils.setField(service,"wmsReadonlyJdbc",jdbc);ReflectionTestUtils.setField(service,"productWmsReadonlyQueryAdapter",adapter);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void clean(){TenantContextHolder.clear();jdbc.execute("DROP ALL OBJECTS");}
    ProductWmsSkuSummaryDTO sku(long id){
        var d=new ProductWmsSkuSummaryDTO();d.setSkuId(id);d.setTotalQuantity(new BigDecimal("-1.250"));return d;
    }
    @Test void onlyPublishedMappedSkusAreVisible(){
        when(adapter.getSkuSummary(9L)).thenReturn(List.of(sku(101),sku(102),sku(103),sku(104)));
        var result=service.getWmsSkuSummary(9L);
        assertEquals(1,result.size());assertEquals(101,result.getFirst().getSkuId());
        assertNull(result.getFirst().getSellingPrice());assertEquals(new BigDecimal("-1.250"),result.getFirst().getTotalQuantity());
    }
    @Test void unmappedItemAndAdapterFailureAreNotReportedAsZeroStock(){
        when(adapter.getSkuSummary(9L)).thenReturn(List.of(sku(102)));
        assertThrows(ServiceException.class,()->service.getWmsSkuSummary(9L));
        when(adapter.getSkuSummary(9L)).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class,()->service.getWmsSkuSummary(9L));
    }
    @Test void invalidTenantScopeDoesNotFetchWarehouseData(){
        TenantContextHolder.setTenantId(9L);
        assertThrows(ServiceException.class,()->service.getWmsSkuSummary(9L));verifyNoInteractions(adapter);
    }
    @Test void oldWriteEntrypointsAlwaysFailClosed(){
        assertThrows(ServiceException.class,()->service.updateSkuStock(null));
        assertThrows(ServiceException.class,()->service.reserveSkuStock(null));
        assertThrows(ServiceException.class,()->service.releaseSkuStock(null));
        assertThrows(ServiceException.class,()->service.outboundSkuStock(null));
        assertThrows(ServiceException.class,()->service.inboundReturnSkuStock("return",null));
        assertThrows(ServiceException.class,()->service.syncWmsStockCache(null));
        var legacy=new ProductWmsInventoryDatabaseAdapter();
        assertThrows(ServiceException.class,()->legacy.reserve("order",List.of()));
        assertThrows(ServiceException.class,()->legacy.release("order",List.of()));
        assertThrows(ServiceException.class,()->legacy.outbound("order",List.of()));
        assertThrows(ServiceException.class,()->legacy.inboundReturn("return","order",List.of()));
    }
}
