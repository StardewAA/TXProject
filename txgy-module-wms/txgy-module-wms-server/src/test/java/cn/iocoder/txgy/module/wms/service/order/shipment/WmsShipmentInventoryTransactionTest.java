package cn.iocoder.txgy.module.wms.service.order.shipment;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.*;
import cn.iocoder.txgy.module.wms.service.inventory.*;
import cn.iocoder.txgy.module.wms.service.md.item.*;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real database, order, inventory and history services; only master lookups and remote BPM are mocked. */
@Import({WmsShipmentOrderServiceImpl.class, WmsShipmentOrderDetailServiceImpl.class,
        WmsInventoryServiceImpl.class, WmsInventoryHistoryServiceImpl.class,
        cn.iocoder.txgy.module.wms.service.integration.WmsShortageService.class,
        cn.iocoder.txgy.module.wms.service.integration.WmsShortageTestConfiguration.class})
class WmsShipmentInventoryTransactionTest extends BaseDbUnitTest {
    @Resource WmsShipmentOrderServiceImpl service;
    @Resource WmsInventoryServiceImpl inventoryService;
    @Resource cn.iocoder.txgy.module.wms.service.integration.WmsShortageService shortages;
    @Resource DataSource dataSource;
    @MockitoBean WmsItemSkuService skuService;
    @MockitoBean WmsItemService itemService;
    @MockitoBean WmsWarehouseService warehouseService;
    @MockitoBean WmsMerchantService merchantService;
    @MockitoBean BpmProcessInstanceApi bpm;
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(inventoryService));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wms_shipment_order(id,no,type,order_time,status,approval_status,execution_status,process_instance_id,warehouse_id) VALUES(901,'TXN-SHIP',202,CURRENT_TIMESTAMP,0,1,0,'txn-process',1)");
        jdbc.update("INSERT INTO wms_shipment_order_detail(id,order_id,sku_id,warehouse_id,quantity) VALUES(901,901,1,1,3),(902,901,2,1,4)");
        jdbc.update("INSERT INTO wms_inventory(id,sku_id,warehouse_id,quantity) VALUES(901,1,1,10),(902,2,1,10)");
        lenient().when(skuService.validateItemSkuExists(2L)).thenReturn(new WmsItemSkuDO().setId(2L).setItemId(2L).setName("SKU2"));
        lenient().when(itemService.validateItemExists(2L)).thenReturn(new WmsItemDO().setId(2L).setName("物料2"));
    }
    @Test void approvalDeductsOnceAndCommitsTwoHistoryRows() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
        assertEquals(0, balance(1).compareTo(BigDecimal.TEN));
        service.updateShipmentOrderApprovalStatus(901L, "txn-process", 2, "通过");
        service.updateShipmentOrderApprovalStatus(901L, "txn-process", 2, "重复回调");
        assertEquals(0, balance(1).compareTo(new BigDecimal("7")));
        assertEquals(0, balance(2).compareTo(new BigDecimal("6")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT execution_status FROM wms_shipment_order WHERE id=901", Integer.class));
    }
    @Test void insufficientSecondLineRollsBackAllStockAndKeepsApprovalResult() {
        jdbc.update("UPDATE wms_inventory SET quantity=1 WHERE sku_id=2");
        assertDoesNotThrow(() -> service.updateShipmentOrderApprovalStatus(901L, "txn-process", 2, "通过"));
        assertEquals(0, balance(1).compareTo(BigDecimal.TEN));
        assertEquals(0, balance(2).compareTo(BigDecimal.ONE));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT approval_status FROM wms_shipment_order WHERE id=901", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT execution_status FROM wms_shipment_order WHERE id=901", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT execution_error FROM wms_shipment_order WHERE id=901", String.class));
    }
    @Test void disabledSkuAfterSubmissionRecordsFailureWithoutStock() {
        doThrow(new IllegalArgumentException("物料已停用")).when(skuService).validateItemSkuListItemEnabled(any());
        assertDoesNotThrow(() -> service.updateShipmentOrderApprovalStatus(901L, "txn-process", 2, "通过"));
        assertEquals(2, jdbc.queryForObject("SELECT approval_status FROM wms_shipment_order WHERE id=901", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT execution_status FROM wms_shipment_order WHERE id=901", Integer.class));
        assertEquals(0, balance(1).compareTo(BigDecimal.TEN));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
    }
    @Test void sourceShortageBlocksApprovalWithoutStockChanges() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            jdbc.update("UPDATE wms_shipment_order SET source_module='MES', biz_order_no='SHORTAGE-TEST-001', approval_status=-1 WHERE id=901");
            jdbc.update("UPDATE wms_inventory SET quantity=1 WHERE sku_id=2");
            when(bpm.createProcessInstance(any(), any())).thenReturn(
                    cn.iocoder.txgy.framework.common.pojo.CommonResult.success("unexpected-process"));
            assertThrows(RuntimeException.class, () -> service.submitShipmentOrderApproval(1L, 901L));
            assertEquals(-1, jdbc.queryForObject("SELECT approval_status FROM wms_shipment_order WHERE id=901", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
            assertEquals(0, balance(2).compareTo(BigDecimal.ONE));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wms_shortage_event", Integer.class));
            assertEquals("ERP", jdbc.queryForObject("SELECT target_system FROM wms_shortage_event", String.class));
            assertEquals("WAITING_INTEGRATION", jdbc.queryForObject("SELECT notification_status FROM wms_shortage_event", String.class));
        } finally {
            cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }
    private BigDecimal balance(int sku) {
        return jdbc.queryForObject("SELECT quantity FROM wms_inventory WHERE sku_id=?", BigDecimal.class, sku);
    }
    @Test void repeatedChecksAllocateSameSkuOnceAndResolveAfterReplenishment() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            jdbc.update("UPDATE wms_shipment_order SET source_module='MES',biz_order_no='GAP-001',approval_status=-1 WHERE id=901");
            jdbc.update("UPDATE wms_shipment_order_detail SET sku_id=1 WHERE id=902");
            jdbc.update("UPDATE wms_inventory SET quantity=5 WHERE sku_id=1");
            assertTrue(shortages.check(901L));
            assertTrue(shortages.check(901L));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wms_shortage_event", Integer.class));
            assertEquals(0, new BigDecimal(shortages.list(901L).getFirst().get("shortageQuantity").toString()).compareTo(new BigDecimal("2")));
            jdbc.update("UPDATE wms_inventory SET quantity=7 WHERE sku_id=1");
            shortages.recheck(901L);
            assertEquals("RESOLVED", shortages.list(901L).getFirst().get("shortageStatus"));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wms_shortage_event", Integer.class));
            when(bpm.createProcessInstance(any(),any())).thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success("after-replenishment"));
            assertEquals("after-replenishment", service.submitShipmentOrderApproval(1L,901L));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history",Integer.class));
        } finally { cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
    @Test void salesShortageTargetsMesAndOtherTenantCannotReadOrRecheck() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            jdbc.update("UPDATE wms_shipment_order SET source_module='ERP',type=201,biz_order_no='SALE-GAP-001',approval_status=-1 WHERE id=901");
            jdbc.update("UPDATE wms_inventory SET quantity=1 WHERE sku_id=2");
            assertTrue(shortages.check(901L));
            assertEquals("MES", shortages.list(901L).getFirst().get("targetSystem"));
            assertEquals("SALES_FINISHED_GOODS_SHORTAGE",shortages.list(901L).getFirst().get("eventType"));
            cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(987L);
            assertThrows(IllegalArgumentException.class,()->shortages.list(901L));
            assertThrows(IllegalArgumentException.class,()->shortages.recheck(901L));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wms_shortage_event",Integer.class));
        } finally { cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
    @Test void approvedSourceFailureRequiresFreshApprovalAfterReplenishment() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            jdbc.update("UPDATE wms_shipment_order SET source_module='MES',biz_order_no='FAILED-GAP-001' WHERE id=901");
            jdbc.update("UPDATE wms_inventory SET quantity=1 WHERE sku_id=2");
            service.updateShipmentOrderApprovalStatus(901L,"txn-process",2,"通过");
            assertEquals("OPEN",shortages.list(901L).getFirst().get("shortageStatus"));
            jdbc.update("UPDATE wms_inventory SET quantity=10 WHERE sku_id=2");
            assertThrows(RuntimeException.class,()->service.completeShipmentOrder(901L));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history",Integer.class));
            shortages.recheck(901L);
            assertEquals("RESOLVED",shortages.list(901L).getFirst().get("shortageStatus"));
            assertEquals(-1,jdbc.queryForObject("SELECT approval_status FROM wms_shipment_order WHERE id=901",Integer.class));
            assertThrows(RuntimeException.class,()->service.completeShipmentOrder(901L));
        } finally { cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
    @Test void approvedButFailedSourceCanBeCancelledWithoutReplenishment() {
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            jdbc.update("UPDATE wms_shipment_order SET source_module='MES',biz_order_no='CANCEL-GAP-001' WHERE id=901");
            jdbc.update("UPDATE wms_inventory SET quantity=1 WHERE sku_id=2");
            service.updateShipmentOrderApprovalStatus(901L,"txn-process",2,"通过");
            service.cancelShipmentOrder(901L);
            assertEquals("CANCELLED",shortages.list(901L).getFirst().get("shortageStatus"));
            assertEquals(5,jdbc.queryForObject("SELECT status FROM wms_shipment_order WHERE id=901",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history",Integer.class));
        } finally { cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
}
