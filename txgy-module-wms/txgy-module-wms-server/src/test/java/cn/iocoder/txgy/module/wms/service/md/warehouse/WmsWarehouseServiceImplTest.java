package cn.iocoder.txgy.module.wms.service.md.warehouse;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi;
import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehouseSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.order.check.WmsCheckOrderService;
import cn.iocoder.txgy.module.wms.service.order.movement.WmsMovementOrderService;
import cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService;
import cn.iocoder.txgy.module.wms.service.permission.WmsWarehouseAccessService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.WAREHOUSE_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.WAREHOUSE_HAS_INVENTORY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(WmsWarehouseServiceImpl.class)
public class WmsWarehouseServiceImplTest extends BaseDbUnitTest {

    @Test
    void savesExplicitFinishedGoodsTypeFromRequest() throws Exception {
        TenantContextHolder.setTenantId(2005L);
        try {

            var request = new com.fasterxml.jackson.databind.ObjectMapper()
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue("{\"code\":\"FG-TEST\",\"name\":\"成品类型测试\",\"sort\":1,\"warehouseType\":\"FINISHED_GOODS\"}", WmsWarehouseSaveReqVO.class);
            Long id = warehouseService.createWarehouse(request);
            org.junit.jupiter.api.Assertions.assertEquals("FINISHED_GOODS", warehouseMapper.selectById(id).getWarehouseType());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Resource
    private WmsWarehouseServiceImpl warehouseService;

    @Resource
    private WmsWarehouseMapper warehouseMapper;

    @MockitoBean
    private WmsInventoryService inventoryService;
    @MockitoBean
    private WmsReceiptOrderService receiptOrderService;
    @MockitoBean
    private WmsShipmentOrderService shipmentOrderService;
    @MockitoBean
    private WmsMovementOrderService movementOrderService;
    @MockitoBean
    private WmsCheckOrderService checkOrderService;
    @MockitoBean
    private WmsWarehousePersonnelService warehousePersonnelService;
    @MockitoBean
    private WmsWarehouseAccessService warehouseAccessService;
    @MockitoBean
    private ErpWmsCallbackApi erpWmsCallbackApi;

    @Test
    public void testCreateWarehouse_codeDuplicate() {
        // mock 数据
        WmsWarehouseDO warehouse = createWarehouse("WH001", "成品仓");
        warehouseMapper.insert(warehouse);

        // 调用，并断言
        WmsWarehouseSaveReqVO reqVO = createWarehouseSaveReqVO();
        assertServiceException(() -> warehouseService.createWarehouse(reqVO), WAREHOUSE_CODE_DUPLICATE);
    }

    @Test
    public void testUpdateWarehouse_codeDuplicate() {
        // mock 数据
        WmsWarehouseDO warehouse = createWarehouse("WH001", "成品仓");
        warehouseMapper.insert(warehouse);
        WmsWarehouseDO updateWarehouse = createWarehouse("WH002", "原料仓");
        warehouseMapper.insert(updateWarehouse);

        // 调用，并断言
        WmsWarehouseSaveReqVO reqVO = createWarehouseSaveReqVO();
        reqVO.setId(updateWarehouse.getId());
        assertServiceException(() -> warehouseService.updateWarehouse(reqVO), WAREHOUSE_CODE_DUPLICATE);
    }

    @Test
    public void testDeleteWarehouse_hasInventory() {
        // mock 数据
        WmsWarehouseDO warehouse = createWarehouse();
        warehouseMapper.insert(warehouse);
        when(inventoryService.getInventoryCountByWarehouseId(warehouse.getId())).thenReturn(1L);

        // 调用，并断言
        assertServiceException(() -> warehouseService.deleteWarehouse(warehouse.getId()), WAREHOUSE_HAS_INVENTORY);
        assertNotNull(warehouseMapper.selectById(warehouse.getId()));
    }

    @Test
    public void testCreateWarehouse_doesNotRequireErpMirror() {
        TenantContextHolder.setTenantId(2005L);
        try {


            warehouseService.createWarehouse(createWarehouseSaveReqVO());

            org.mockito.Mockito.verifyNoInteractions(erpWmsCallbackApi);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    public void testUpdateWarehouse_preservesVersionWithoutErpMirror() {
        TenantContextHolder.setTenantId(2005L);
        try {
            WmsWarehouseDO warehouse = createWarehouse("WH001", "旧仓库");
            warehouse.setWarehouseType("RAW_MATERIAL");
            warehouse.setStatus(0);
            warehouse.setDataVersion(7L);
            warehouseMapper.insert(warehouse);


            WmsWarehouseSaveReqVO reqVO = createWarehouseSaveReqVO();
            reqVO.setId(warehouse.getId());
            reqVO.setName("新仓库");
            reqVO.setWarehouseType("FINISHED_GOODS");
            warehouseService.updateWarehouse(reqVO);

            org.junit.jupiter.api.Assertions.assertEquals("FINISHED_GOODS",
                    warehouseMapper.selectById(warehouse.getId()).getWarehouseType());

            org.mockito.Mockito.verifyNoInteractions(erpWmsCallbackApi);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static WmsWarehouseDO createWarehouse() {
        return createWarehouse("WH001", "成品仓");
    }

    private static WmsWarehouseDO createWarehouse(String code, String name) {
        return WmsWarehouseDO.builder()
                .code(code)
                .name(name)
                .sort(1)
                .build();
    }

    private static WmsWarehouseSaveReqVO createWarehouseSaveReqVO() {
        WmsWarehouseSaveReqVO reqVO = new WmsWarehouseSaveReqVO();
        reqVO.setCode("WH001");
        reqVO.setName("半成品仓");
        reqVO.setSort(1);
        return reqVO;
    }

}
