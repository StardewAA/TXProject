package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryReadMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the production annotated SQL against an isolated database, without tenant
 * interceptors masking a missing explicit tenant predicate in the read-only contract. */
class WmsInventoryReadApiRegressionTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private WmsInventoryReadApiService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        var dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:inventory_read_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // Only this test database is initialized; the shared test schema is untouched.
        jdbc.execute("CREATE TABLE wms_item (id BIGINT PRIMARY KEY, code VARCHAR(64), tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE wms_item_sku (id BIGINT PRIMARY KEY, item_id BIGINT, code VARCHAR(64), tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE wms_warehouse (id BIGINT PRIMARY KEY, code VARCHAR(64), tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE wms_inventory (id BIGINT PRIMARY KEY, sku_id BIGINT, warehouse_id BIGINT, quantity DECIMAL(20,2), update_time TIMESTAMP, tenant_id BIGINT, deleted INT, UNIQUE (sku_id, warehouse_id))");
        jdbc.update("INSERT INTO wms_item VALUES (1,'MAT-A',1,0),(2,'MAT-A',2,0),(3,'DELETED-MAT',1,1),(4,'NO-SKU',1,0),(5,'OTHER-MAT',2,0)");
        jdbc.update("INSERT INTO wms_item_sku VALUES (11,1,'MAT-A-DEFAULT',1,0),(12,1,'MAT-A-LARGE',1,0),(21,2,'MAT-A-DEFAULT',2,0),(31,3,'DELETED-MAT-DEFAULT',1,0),(13,1,'DELETED-SKU',1,1),(22,1,'WRONG-TENANT-SKU',2,0)");
        jdbc.update("INSERT INTO wms_warehouse VALUES (101,'WH-A',1,0),(102,'WH-B',1,0),(201,'WH-A',2,0),(103,'DELETED-WH',1,1),(202,'OTHER-WH',2,0)");
        jdbc.update("INSERT INTO wms_inventory VALUES (1,11,101,12.34,TIMESTAMP '2026-09-20 08:09:10',1,0),(2,12,101,5.67,TIMESTAMP '2026-09-20 09:10:11',1,0),(3,21,201,999.99,TIMESTAMP '2026-09-20 08:09:10',2,0),(4,12,102,88.88,TIMESTAMP '2026-09-20 08:09:10',1,1),(5,11,102,77.77,TIMESTAMP '2026-09-20 08:09:10',2,0)");

        var configuration = new Configuration(new Environment("inventory-read-regression",
                new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(WmsInventoryReadMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        service = new WmsInventoryReadApiService(session.getMapper(WmsInventoryReadMapper.class));
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (session != null) {
            session.close();
        }
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void preservesSkuWarehouseGrainAndDecimalQuantity() {
        // Catches material-level aggregation, lost warehouses/SKUs, or wrong DTO aliases.
        var rows = service.getSnapshot(null, null);
        assertEquals(List.of("11:101", "12:101", "11:102", "12:102"),
                rows.stream().map(row -> row.getSkuId() + ":" + row.getWarehouseId()).toList());
        var first = rows.get(0);
        assertEquals("MAT-A", first.getMaterialCode());
        assertEquals("WH-A", first.getWarehouseCode());
        assertEquals("MAT-A-DEFAULT", first.getSkuCode());
        assertQuantity("12.34", first);
        assertEquals(LocalDateTime.of(2026, 9, 20, 8, 9, 10), first.getUpdatedAt());
        assertEquals("MAT-A-LARGE", rows.get(1).getSkuCode());
        assertQuantity("5.67", rows.get(1));
    }

    @Test
    void excludesOtherTenantsFromEveryJoinedTableAndExistenceQueries() {
        // Same codes in two tenants must resolve to different IDs and quantities.
        var firstTenant = service.getSnapshot(List.of("MAT-A"), List.of("WH-A"));
        assertEquals(List.of(11L, 12L), firstTenant.stream().map(WmsInventorySnapshotRespDTO::getSkuId).toList());
        assertEquals(List.of("MAT-A", "NO-SKU"), service.getExistingMaterialCodes(null));
        assertEquals(List.of("WH-A", "WH-B"), service.getExistingWarehouseCodes(null));

        TenantContextHolder.setTenantId(2L);
        var secondTenant = service.getSnapshot(List.of("MAT-A"), List.of("WH-A"));
        assertEquals(1, secondTenant.size());
        assertEquals(21L, secondTenant.get(0).getSkuId());
        assertEquals(201L, secondTenant.get(0).getWarehouseId());
        assertQuantity("999.99", secondTenant.get(0));
        assertEquals(List.of("MAT-A", "OTHER-MAT"), service.getExistingMaterialCodes(null));
        assertEquals(List.of("OTHER-WH", "WH-A"), service.getExistingWarehouseCodes(null));
    }

    @Test
    void missingDeletedAndForeignTenantInventoryReturnZeroWithNoTimestamp() {
        // Catches changing LEFT JOIN to INNER JOIN or removing inventory deletion/tenant predicates.
        var rows = service.getSnapshot(List.of("MAT-A"), List.of("WH-B"));
        assertEquals(2, rows.size());
        for (var row : rows) {
            assertQuantity("0", row);
            assertNull(row.getUpdatedAt());
        }
        jdbc.update("DELETE FROM wms_inventory WHERE id IN (4,5)");
        session.clearCache();
        var absentRows = service.getSnapshot(List.of("MAT-A"), List.of("WH-B"));
        assertEquals(rows, absentRows);
    }

    @Test
    void deletedMasterDataIsExcludedButMaterialWithoutSkuStillExists() {
        // Catches missing deleted predicates on item, SKU and warehouse independently.
        assertTrue(service.getSnapshot(List.of("DELETED-MAT"), null).isEmpty());
        assertTrue(service.getSnapshot(null, List.of("DELETED-WH")).isEmpty());
        assertEquals(List.of(11L, 12L), service.getSnapshot(List.of("MAT-A"), List.of("WH-A"))
                .stream().map(WmsInventorySnapshotRespDTO::getSkuId).toList());
        assertEquals(List.of("NO-SKU"), service.getExistingMaterialCodes(List.of("NO-SKU", "DELETED-MAT")));
        assertTrue(service.getSnapshot(List.of("NO-SKU"), null).isEmpty());
        assertTrue(service.getExistingWarehouseCodes(List.of("DELETED-WH")).isEmpty());
    }

    @Test
    void filtersCodesExactlyAndTreatsEmptyCollectionsAsUnrestricted() {
        // Catches ignored filters, swapped parameters, empty IN clauses and wildcard matching.
        assertEquals(2, service.getSnapshot(List.of("MAT-A", "UNKNOWN"), List.of("WH-A", "UNKNOWN")).size());
        assertTrue(service.getSnapshot(List.of("UNKNOWN"), null).isEmpty());
        assertTrue(service.getSnapshot(null, List.of("UNKNOWN")).isEmpty());
        assertTrue(service.getSnapshot(List.of("MAT-%"), null).isEmpty());
        assertTrue(service.getSnapshot(List.of(" MAT-A "), null).isEmpty());
        assertEquals(service.getSnapshot(null, null), service.getSnapshot(List.of(), List.of()));
        assertEquals(List.of("MAT-A"), service.getExistingMaterialCodes(List.of("MAT-A", "UNKNOWN")));
        assertEquals(List.of("WH-B"), service.getExistingWarehouseCodes(List.of("WH-B", "UNKNOWN")));
        assertTrue(service.getExistingMaterialCodes(List.of("UNKNOWN")).isEmpty());
        assertTrue(service.getExistingWarehouseCodes(List.of("UNKNOWN")).isEmpty());
        assertEquals(service.getExistingMaterialCodes(null), service.getExistingMaterialCodes(List.of()));
        assertEquals(service.getExistingWarehouseCodes(null), service.getExistingWarehouseCodes(List.of()));
    }

    @Test
    void readsLeaveAllMasterDataAndInventoryUnchanged() {
        // Catches accidental updates/deletes/inserts anywhere in this service call chain.
        var tables = List.of("wms_item", "wms_item_sku", "wms_warehouse", "wms_inventory");
        var before = tables.stream().map(this::tableRows).toList();
        service.getSnapshot(null, null);
        service.getExistingMaterialCodes(null);
        service.getExistingWarehouseCodes(null);
        assertEquals(before, tables.stream().map(this::tableRows).toList());
    }

    @Test
    void missingTenantFailsInsteadOfReturningSuccessfulEmptyOrZeroStock() {
        // Catches weakening getRequiredTenantId to an optional tenant or swallowing the failure.
        TenantContextHolder.clear();
        assertThrows(NullPointerException.class, () -> service.getSnapshot(null, null));
        assertThrows(NullPointerException.class, () -> service.getExistingMaterialCodes(null));
        assertThrows(NullPointerException.class, () -> service.getExistingWarehouseCodes(null));
    }

    private List<Map<String, Object>> tableRows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    private static void assertQuantity(String expected, WmsInventorySnapshotRespDTO row) {
        assertNotNull(row.getQuantity());
        assertEquals(0, new BigDecimal(expected).compareTo(row.getQuantity()));
    }
}
