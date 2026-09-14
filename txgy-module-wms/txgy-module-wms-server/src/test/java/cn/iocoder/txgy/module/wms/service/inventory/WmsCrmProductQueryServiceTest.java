package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

@Import(WmsCrmProductQueryService.class)
class WmsCrmProductQueryServiceTest extends BaseDbUnitTest {
    @Resource private WmsCrmProductQueryService service;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(2005L);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wms_item(id,code,name,unit,category_id,tenant_id,status,deleted) VALUES(1,'BOARD','Board','包',1,2005,0,0),(2,'OTHER','Other','包',1,1,0,0),(3,'OFF','Disabled','包',1,2005,1,0),(4,'DEL','Deleted','包',1,2005,0,1)");
        jdbc.update("INSERT INTO wms_item_sku(id,item_id,code,name,tenant_id,deleted) VALUES(1,1,'BOARD-50','50mm',2005,0),(2,1,'BOARD-80','80mm',2005,0),(3,2,'OTHER','Other',1,0),(4,2,'FOREIGN','Foreign relation',2005,0),(5,3,'OFF','Off',2005,0),(6,4,'DEL','Deleted parent',2005,0),(7,1,'DEL-SKU','Deleted SKU',2005,1)");
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void readsEnabledProductsWithoutStockAndExcludesDeletedAndForeignRelations() {
        var page = service.getPage(new WmsCrmProductPageReqVO());
        assertEquals(2L, page.getTotal());
        var row = page.getList().getFirst();
        assertEquals(1L, row.getItemId());
        assertEquals(1L, row.getSkuId());
        assertEquals("BOARD", row.getMaterialCode());
        assertEquals("Board", row.getMaterialName());
        assertEquals("BOARD-50", row.getSkuCode());
        assertEquals("50mm", row.getSkuName());
        assertEquals("包", row.getUnitName());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory_history", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM wms_item_sku", Integer.class));
        TenantContextHolder.setTenantId(1L);
        assertEquals("OTHER", service.getPage(new WmsCrmProductPageReqVO()).getList().getFirst().getSkuCode());
    }

    @Test void filtersAndPaginatesInStableSkuOrder() {
        var q = new WmsCrmProductPageReqVO();
        q.setPageSize(1);
        q.setPageNo(2);
        assertEquals(2L, service.getPage(q).getTotal());
        assertEquals("BOARD-80", service.getPage(q).getList().getFirst().getSkuCode());
        q.setPageNo(1);
        q.setMaterialName("oar");
        q.setMaterialCode("BOARD");
        q.setSkuCode("BOARD-50");
        assertEquals(1L, service.getPage(q).getTotal());
        q.setMaterialCode("OTHER");
        assertEquals(0L, service.getPage(q).getTotal());
        q.setMaterialCode("' OR 1=1 --");
        assertEquals(0L, service.getPage(q).getTotal());
    }

    @Test void rejectsMissingTenantAndUnboundedPages() {
        var q = new WmsCrmProductPageReqVO();
        for (int size : new int[]{-1, 0, 201}) {
            q.setPageSize(size);
            assertThrows(IllegalArgumentException.class, () -> service.getPage(q));
        }
        q.setPageSize(10);
        q.setPageNo(0);
        assertThrows(IllegalArgumentException.class, () -> service.getPage(q));
        q.setPageNo(1);
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> service.getPage(q));
    }
}
