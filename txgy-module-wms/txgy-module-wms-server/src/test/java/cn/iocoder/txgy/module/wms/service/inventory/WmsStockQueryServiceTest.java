package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockPageReqDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@Import(WmsStockQueryService.class)
class WmsStockQueryServiceTest extends BaseDbUnitTest {
    @Resource private WmsStockQueryService service;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(2005L);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wms_warehouse(id,code,name,tenant_id) VALUES(1,'WH-A','A',2005),(2,'WH-B','B',2005),(3,'OTHER','Other tenant',1)");
        jdbc.update("INSERT INTO wms_item(id,code,name,unit,category_id,tenant_id) VALUES(1,'BOARD','Board','包',1,2005),(2,'BOARD','Other tenant','包',1,1)");
        jdbc.update("INSERT INTO wms_item_sku(id,item_id,code,name,tenant_id) VALUES(1,1,'BOARD-50','50mm',2005),(2,1,'BOARD-80','80mm',2005),(3,2,'OTHER','Other',1)");
        jdbc.update("INSERT INTO wms_inventory(sku_id,warehouse_id,quantity,tenant_id,deleted) VALUES(1,1,39,2005,0),(2,1,60,2005,0),(1,2,7,2005,0),(3,3,999,1,0),(2,2,900,2005,1)");
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void readsLiveSkuBalancesWithoutErpMirrorAndPreservesTenantIsolation() {
        var request = new WmsStockPageReqDTO();
        request.setWarehouseCode("WH-A");
        var page = service.getStockPage(request);
        assertEquals(2L, page.getTotal());
        assertEquals("BOARD-50", page.getList().get(0).getSkuCode());
        assertEquals(0, new BigDecimal("39").compareTo(page.getList().get(0).getQuantity()));
        assertEquals(0, new BigDecimal("60").compareTo(page.getList().get(1).getQuantity()));
        assertEquals(2, service.getWarehouses().size());
        assertEquals(1L, service.getWarehouses().getFirst().getId());
        // A committed WMS change is visible on the next query, without an ERP refresh.
        jdbc.update("UPDATE wms_inventory SET quantity=40 WHERE sku_id=1 AND warehouse_id=1 AND deleted=0");
        assertEquals(0, new BigDecimal("40").compareTo(service.getStockPage(request).getList().get(0).getQuantity()));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory", Integer.class));
    }

    @Test void paginatesAndFiltersCodesWithoutCrossTenantFallback() {
        var request = new WmsStockPageReqDTO();
        request.setPageSize(1);
        request.setPageNo(2);
        assertEquals(3L, service.getStockPage(request).getTotal());
        assertEquals(1, service.getStockPage(request).getList().size());
        request.setWarehouseCode("OTHER");
        assertEquals(0L, service.getStockPage(request).getTotal());
        request.setWarehouseCode(null);
        request.setPageNo(1);
        request.setSkuCode("BOARD-80");
        assertEquals(1L, service.getStockPage(request).getTotal());
    }

    @Test void filtersExactMappedSkuWarehouseAndReturnsMappingIds() {
        var request = new WmsStockPageReqDTO();
        request.setItemId(1L);
        request.setSkuId(1L);
        request.setWarehouseId(2L);
        var page = service.getStockPage(request);
        assertEquals(1L, page.getTotal());
        assertEquals(1L, page.getList().getFirst().getItemId());
        assertEquals(1L, page.getList().getFirst().getSkuId());
        assertEquals(2L, page.getList().getFirst().getWarehouseId());
        assertEquals(0, new BigDecimal("7").compareTo(page.getList().getFirst().getQuantity()));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0L, service.getStockPage(request).getTotal());
    }

    @Test void refusesQueriesWithoutTenantContext() {
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> service.getStockPage(new WmsStockPageReqDTO()));
        assertThrows(RuntimeException.class, () -> service.getWarehouses());
    }

    @Test void listsSkuMasterDataEvenWithoutStockAndRejectsDeletedOrForeignRelations() {
        jdbc.update("INSERT INTO wms_item_sku(id,item_id,code,name,tenant_id) VALUES(4,1,'BOARD-100','100mm',2005),(5,2,'FOREIGN-LINK','Invalid',2005)");
        jdbc.update("INSERT INTO wms_item_sku(id,item_id,code,name,tenant_id,deleted) VALUES(6,1,'DELETED','Deleted',2005,1)");
        var skus = service.getSkuOptions();
        assertEquals(java.util.List.of("BOARD-50", "BOARD-80", "BOARD-100"),
                skus.stream().map(s -> s.getSkuCode()).toList());
        assertEquals("BOARD", skus.get(2).getMaterialCode());
        assertEquals(1L, skus.get(2).getItemId());
        assertEquals(4L, skus.get(2).getSkuId());
        assertEquals("包", skus.get(2).getUnitName());
        TenantContextHolder.setTenantId(1L);
        assertEquals(java.util.List.of("OTHER"), service.getSkuOptions().stream().map(s -> s.getSkuCode()).toList());
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> service.getSkuOptions());
    }
}
