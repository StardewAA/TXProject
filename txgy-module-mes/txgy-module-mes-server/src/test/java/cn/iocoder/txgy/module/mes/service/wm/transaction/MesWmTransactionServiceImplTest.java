package cn.iocoder.txgy.module.mes.service.wm.transaction;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.batch.MesWmBatchDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.materialstock.MesWmMaterialStockDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.warehouse.MesWmWarehouseAreaDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.warehouse.MesWmWarehouseDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.warehouse.MesWmWarehouseLocationDO;
import cn.iocoder.txgy.module.mes.dal.mysql.wm.transaction.MesWmTransactionMapper;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmQualityStatusEnum;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmTransactionTypeEnum;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.wm.batch.MesWmBatchService;
import cn.iocoder.txgy.module.mes.service.wm.materialstock.MesWmMaterialStockService;
import cn.iocoder.txgy.module.mes.service.wm.transaction.dto.MesWmTransactionSaveReqDTO;
import cn.iocoder.txgy.module.mes.service.wm.warehouse.MesWmWarehouseAreaService;
import cn.iocoder.txgy.module.mes.service.wm.warehouse.MesWmWarehouseLocationService;
import cn.iocoder.txgy.module.mes.service.wm.warehouse.MesWmWarehouseService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.WM_TRANSACTION_BATCH_QUALITY_BLOCKED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MesWmTransactionServiceImpl} 的单元测试
 *
 * 重点验证 R4 批次质量门禁矩阵：
 * 入库：0待检/1合格/5让步 放行；2不合格 仅虚拟线边库放行；3冻结/4隔离 拦截
 * 出库：1合格/5让步 放行；0待检/2不合格/3冻结/4隔离 拦截
 *
 * @author OPENLAB BS
 */
@Import(MesWmTransactionServiceImpl.class)
public class MesWmTransactionServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MesWmTransactionServiceImpl transactionService;

    @Resource
    private MesWmTransactionMapper transactionMapper;

    @MockitoBean
    private MesWmMaterialStockService materialStockService;
    @MockitoBean
    private MesWmBatchService batchService;
    @MockitoBean
    private MesMdItemService itemService;
    @MockitoBean
    private MesWmWarehouseService warehouseService;
    @MockitoBean
    private MesWmWarehouseLocationService locationService;
    @MockitoBean
    private MesWmWarehouseAreaService areaService;

    private Long batchId;
    private Long warehouseId;

    @BeforeEach
    public void setUp() {
        batchId = randomLongId();
        warehouseId = randomLongId();
        // 默认：库存记录存在且不冻结；仓库/库区/库位不冻结
        MesWmMaterialStockDO stock = new MesWmMaterialStockDO();
        stock.setId(randomLongId());
        stock.setFrozen(false);
        when(materialStockService.getOrCreateMaterialStock(anyLong(), anyLong(), anyLong(), anyLong(),
                any(), any(), any(), any())).thenReturn(stock);
        when(warehouseService.validateWarehouseExists(anyLong()))
                .thenReturn(new MesWmWarehouseDO().setId(warehouseId).setFrozen(false));
        when(locationService.validateWarehouseLocationExists(anyLong()))
                .thenReturn(new MesWmWarehouseLocationDO().setId(1L).setFrozen(false));
        when(areaService.validateWarehouseAreaExists(anyLong()))
                .thenReturn(new MesWmWarehouseAreaDO().setId(1L).setFrozen(false));
        // 物料未启用批次管理，避免触发“批次号必填”校验
        when(itemService.validateItemExists(anyLong()))
                .thenReturn(new MesMdItemDO().setId(1L).setBatchFlag(false));
    }

    /** 构建事务 DTO（入库正数 / 出库负数） */
    private MesWmTransactionSaveReqDTO buildReqDTO(boolean inbound) {
        return new MesWmTransactionSaveReqDTO()
                .setType(inbound ? MesWmTransactionTypeEnum.IN.getType() : MesWmTransactionTypeEnum.OUT.getType())
                .setBizType(1).setBizId(randomLongId()).setBizCode("BIZ-001").setBizLineId(randomLongId())
                .setItemId(1L)
                .setQuantity(inbound ? BigDecimal.TEN : BigDecimal.TEN.negate())
                .setBatchId(batchId).setBatchCode("BATCH-GATE-001")
                .setWarehouseId(warehouseId).setLocationId(1L).setAreaId(1L);
    }

    /** mock 批次质量状态 */
    private void mockBatchStatus(Integer qualityStatus) {
        when(batchService.getBatch(batchId)).thenReturn(MesWmBatchDO.builder()
                .id(batchId).code("BATCH-GATE-001").qualityStatus(qualityStatus).build());
    }

    /** 断言门禁拦截 */
    private void assertBlocked(boolean inbound, Integer qualityStatus) {
        mockBatchStatus(qualityStatus);
        MesWmTransactionSaveReqDTO reqDTO = buildReqDTO(inbound);
        ServiceException ex = assertThrows(ServiceException.class, () -> transactionService.createTransaction(reqDTO));
        assertEquals(WM_TRANSACTION_BATCH_QUALITY_BLOCKED.getCode(), ex.getCode());
        // 门禁拦截时不允许产生任何库存影响
        verify(materialStockService, never()).updateMaterialStockQuantity(anyLong(), any(), anyBoolean());
    }

    /** 断言放行并真实落流水 */
    private void assertAllowed(boolean inbound, Integer qualityStatus) {
        mockBatchStatus(qualityStatus);
        Long transactionId = transactionService.createTransaction(buildReqDTO(inbound));
        assertNotNull(transactionId);
        assertNotNull(transactionMapper.selectById(transactionId));
        verify(materialStockService).updateMaterialStockQuantity(anyLong(), any(), anyBoolean());
    }

    // ==================== 入库方向 ====================

    @Test
    public void testGate_inbound_pending_allowed() {
        assertAllowed(true, MesWmQualityStatusEnum.PENDING.getStatus());
    }

    @Test
    public void testGate_inbound_pass_allowed() {
        assertAllowed(true, MesWmQualityStatusEnum.PASS.getStatus());
    }

    @Test
    public void testGate_inbound_concession_allowed() {
        assertAllowed(true, MesWmQualityStatusEnum.CONCESSION.getStatus());
    }

    @Test
    public void testGate_inbound_fail_normalWarehouse_blocked() {
        // 普通仓库：不合格禁止入库
        assertBlocked(true, MesWmQualityStatusEnum.FAIL.getStatus());
    }

    @Test
    public void testGate_inbound_fail_virtualWarehouse_allowed() {
        // 虚拟线边库：不合格放行（IPQC 拆分产物入线边库属正常流程）
        MesWmWarehouseDO virtualWarehouse = new MesWmWarehouseDO()
                .setId(warehouseId).setCode(MesWmWarehouseDO.WIP_VIRTUAL_WAREHOUSE).setFrozen(false);
        when(warehouseService.getWarehouseByCode(MesWmWarehouseDO.WIP_VIRTUAL_WAREHOUSE))
                .thenReturn(virtualWarehouse);
        assertAllowed(true, MesWmQualityStatusEnum.FAIL.getStatus());
    }

    @Test
    public void testGate_inbound_frozen_blocked() {
        assertBlocked(true, MesWmQualityStatusEnum.FROZEN.getStatus());
    }

    @Test
    public void testGate_inbound_quarantine_blocked() {
        assertBlocked(true, MesWmQualityStatusEnum.QUARANTINE.getStatus());
    }

    // ==================== 出库方向 ====================

    @Test
    public void testGate_outbound_pass_allowed() {
        assertAllowed(false, MesWmQualityStatusEnum.PASS.getStatus());
    }

    @Test
    public void testGate_outbound_concession_allowed() {
        assertAllowed(false, MesWmQualityStatusEnum.CONCESSION.getStatus());
    }

    @Test
    public void testGate_outbound_pending_blocked() {
        assertBlocked(false, MesWmQualityStatusEnum.PENDING.getStatus());
    }

    @Test
    public void testGate_outbound_fail_blocked() {
        assertBlocked(false, MesWmQualityStatusEnum.FAIL.getStatus());
    }

    @Test
    public void testGate_outbound_frozen_blocked() {
        assertBlocked(false, MesWmQualityStatusEnum.FROZEN.getStatus());
    }

    @Test
    public void testGate_outbound_quarantine_blocked() {
        assertBlocked(false, MesWmQualityStatusEnum.QUARANTINE.getStatus());
    }

    // ==================== 边界 ====================

    @Test
    public void testGate_nullBatchId_allowed() {
        // 无批次（物料未启用批次管理）不参与门禁
        MesWmTransactionSaveReqDTO reqDTO = buildReqDTO(true);
        reqDTO.setBatchId(null).setBatchCode(null);
        Long transactionId = transactionService.createTransaction(reqDTO);
        assertNotNull(transactionId);
    }

    @Test
    public void testGate_nullQualityStatus_allowed() {
        // 历史数据质量状态为空：按无门禁处理
        assertAllowed(true, null);
    }

}
