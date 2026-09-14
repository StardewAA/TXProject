package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockHistoryPageReqDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@Import(WmsStockHistoryQueryService.class)
class WmsStockHistoryQueryServiceTest extends BaseDbUnitTest {
    @Resource private WmsStockHistoryQueryService service;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(2005L);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wms_warehouse(id,code,name,tenant_id) VALUES(1,'WH-A','A',2005),(2,'WH-B','B',2005),(3,'OTHER','Other tenant',1)");
        jdbc.update("INSERT INTO wms_item(id,code,name,unit,category_id,tenant_id) VALUES(1,'BOARD','Board','包',1,2005),(2,'OTHER','Other tenant','包',1,1)");
        jdbc.update("INSERT INTO wms_item_sku(id,item_id,code,name,tenant_id) VALUES(1,1,'BOARD-50','50mm',2005),(3,2,'OTHER','Other',1)");
        jdbc.update("INSERT INTO wms_inventory(sku_id,warehouse_id,quantity,tenant_id) VALUES(1,1,12,2005)");
        jdbc.update("INSERT INTO wms_receipt_order(id,no,type,order_time,warehouse_id,biz_order_no,tenant_id) VALUES(1,'RK-1',1,CURRENT_TIMESTAMP,1,'PO-1',2005),(3,'RK-OTHER',1,CURRENT_TIMESTAMP,3,'SECRET',1)");
        jdbc.update("INSERT INTO wms_shipment_order(id,no,type,order_time,warehouse_id,biz_order_no,tenant_id) VALUES(1,'CK-1',1,CURRENT_TIMESTAMP,1,'SO-1',2005)");
        jdbc.update("INSERT INTO wms_receipt_order(id,no,type,order_time,warehouse_id,biz_order_no,business_chain_no,tenant_id) VALUES(4,'APP-4',1,CURRENT_TIMESTAMP,1,'ERP-4','CHAIN-4',2005)");
        jdbc.update("INSERT INTO wms_inventory_history(id,sku_id,warehouse_id,quantity,before_quantity,after_quantity,order_id,order_no,order_type,create_time,tenant_id,deleted) VALUES"
                + "(1,1,1,10,0,10,1,'RK-1',1,'2026-09-01 10:00:00',2005,0),"
                + "(2,1,2,-3,10,7,1,'CK-1',2,'2026-09-02 10:00:00',2005,0),"
                + "(3,99,99,5,0,5,99,'HISTORIC',3,'2026-09-03 10:00:00',2005,0),"
                + "(4,1,1,2,10,12,4,'APP-4',1,'2026-09-03 10:00:00',2005,0),"
                + "(5,3,3,9,0,9,3,'RK-OTHER',1,'2026-09-04 10:00:00',1,0),"
                + "(6,1,1,99,0,99,1,'DELETED',1,'2026-09-05 10:00:00',2005,1),"
                + "(7,3,3,1,0,1,3,'BAD-RELATION',1,'2026-09-01 10:00:00',2005,0)");
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void preservesHistoricalRowsAndNeverLeaksForeignTenantRelationsOrMutatesStock() {
        var rows = service.getHistoryPage(new WmsStockHistoryPageReqDTO()).getList();
        assertEquals(5, rows.size());
        assertEquals(4L, rows.get(0).getId());
        assertEquals("ERP-4", rows.get(0).getSourceOrderNo());
        assertEquals("CHAIN-4", rows.get(0).getBusinessChainNo());
        assertEquals(3L, rows.get(1).getId());
        assertNull(rows.get(1).getSkuCode());
        var foreign = rows.stream().filter(r -> r.getId() == 7L).findFirst().orElseThrow();
        assertNull(foreign.getSkuCode());
        assertNull(foreign.getWarehouseCode());
        assertNull(foreign.getSourceOrderNo());
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
        assertEquals(0, new BigDecimal("12").compareTo(jdbc.queryForObject("SELECT quantity FROM wms_inventory", BigDecimal.class)));
    }

    @Test void filtersSourceTypeCodesDatesAndPaginatesWithStableOrder() {
        var q = new WmsStockHistoryPageReqDTO();
        q.setPageSize(1); q.setPageNo(2);
        assertEquals(5L, service.getHistoryPage(q).getTotal());
        assertEquals(3L, service.getHistoryPage(q).getList().getFirst().getId());
        q.setPageNo(1); q.setSourceOrderNo("SO-1");
        assertEquals(2L, service.getHistoryPage(q).getList().getFirst().getId());
        q.setSourceOrderNo(null); q.setMaterialCode("BOARD"); q.setSkuCode("BOARD-50"); q.setWarehouseCode("WH-A");
        q.setStartTime(LocalDateTime.of(2026,9,2,0,0)); q.setEndTime(LocalDateTime.of(2026,9,3,10,0));
        assertEquals(1L, service.getHistoryPage(q).getTotal());
        assertEquals("APP-4", service.getHistoryPage(q).getList().getFirst().getOrderNo());
        q.setOrderNo("RK-1"); assertEquals(0L, service.getHistoryPage(q).getTotal());
        q.setOrderNo(null); q.setOrderType(2); assertEquals(0L, service.getHistoryPage(q).getTotal());
    }

    @Test void hidesDeletedMasterDataAndRequiresTenant() {
        jdbc.update("UPDATE wms_item_sku SET deleted=1 WHERE id=1");
        jdbc.update("UPDATE wms_warehouse SET deleted=1 WHERE id=1");
        jdbc.update("UPDATE wms_receipt_order SET deleted=1 WHERE id=4");
        var row = service.getHistoryPage(new WmsStockHistoryPageReqDTO()).getList().getFirst();
        assertNull(row.getSkuCode()); assertNull(row.getWarehouseCode()); assertNull(row.getBusinessChainNo());
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> service.getHistoryPage(new WmsStockHistoryPageReqDTO()));
    }
}
