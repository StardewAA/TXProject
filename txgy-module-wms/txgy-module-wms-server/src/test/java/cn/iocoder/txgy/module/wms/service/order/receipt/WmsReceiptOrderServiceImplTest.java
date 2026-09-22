package cn.iocoder.txgy.module.wms.service.order.receipt;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.detail.WmsReceiptOrderDetailSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDetailDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderDetailMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderMapper;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsReceiptOrderTypeEnum;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemQualityReportService;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import cn.iocoder.txgy.module.wms.enums.md.WmsItemQualityStatusEnum;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehousePersonnelService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_DETAIL_REQUIRED;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_STATUS_NOT_PREPARE;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_STATUS_NOT_DELETABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_ITEM_QUALITY_NOT_QUALIFIED;

@Import({WmsReceiptOrderServiceImpl.class, WmsReceiptOrderDetailServiceImpl.class})
public class WmsReceiptOrderServiceImplTest extends BaseDbUnitTest {

    @Resource
    private WmsReceiptOrderServiceImpl receiptOrderService;

    @Resource
    private WmsReceiptOrderMapper receiptOrderMapper;
    @Resource
    private WmsReceiptOrderDetailMapper receiptOrderDetailMapper;

    @MockitoBean
    private WmsWarehouseService warehouseService;
    @MockitoBean
    private WmsWarehousePersonnelService warehousePersonnelService;
    @MockitoBean
    private WmsMerchantService merchantService;
    @MockitoBean
    private WmsItemSkuService itemSkuService;
    @MockitoBean
    private WmsItemService itemService;
    @MockitoBean
    private WmsItemQualityReportService qualityReportService;
    @MockitoBean
    private WmsInventoryService inventoryService;
    @MockitoBean
    private BpmProcessInstanceApi processInstanceApi;


    @Test
    public void sourceReceiptAcceptancePreservesOriginalIdentityAndSnapshot() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setSourceModule("ERP").setBusinessChainNo("CHAIN").setBizOrderNo("ERP-1");
        receiptOrderMapper.insert(order);
        var detail = createReceiptOrderDetail(order.getId(), 200L, 100L).setSourceLineNo(7).setSourceSnapshotJson("{\"qualityRequiredSnapshot\":true}").setQualityRequiredSnapshot(true);
        receiptOrderDetailMapper.insert(detail);
        var line = createReceiptOrderDetailReqVO(detail.getId(), 200L, "2", "20", "40").setSourceLineNo(99).setSourceSnapshotJson("{}").setQualityRequiredSnapshot(false)
                .setActualQuantity(new BigDecimal("2")).setQualifiedQuantity(BigDecimal.ONE).setUnqualifiedQuantity(BigDecimal.ONE).setQualityImages(List.of("https://example.test/inspection.jpg"));
        var req = createReceiptOrderSaveReqVO(order.getId(), line).setBizOrderNo("ERP-1").setSourceModule("FAKE").setBusinessChainNo("FAKE");
        receiptOrderService.updateReceiptOrder(req);
        var saved = receiptOrderDetailMapper.selectById(detail.getId());
        assertEquals(7, saved.getSourceLineNo()); assertEquals(detail.getSourceSnapshotJson(), saved.getSourceSnapshotJson());
        assertEquals(true, saved.getQualityRequiredSnapshot()); assertEquals("CHAIN", receiptOrderMapper.selectById(order.getId()).getBusinessChainNo());
        assertEquals("ERP", receiptOrderMapper.selectById(order.getId()).getSourceModule());
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getQualifiedQuantity()));
    }

    @Test
    public void zeroQualifiedApprovalCompletesWithoutInventory() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus()).setProcessInstanceId("zero");
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L).setQualifiedQuantity(BigDecimal.ZERO).setUnqualifiedQuantity(new BigDecimal("2")));
        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "zero", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "all rejected");
        assertEquals(1, receiptOrderMapper.selectById(order.getId()).getExecutionStatus());
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void rejectedReceiptCanBeRevisedAndResubmitted() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus()).setProcessInstanceId("old");
        receiptOrderMapper.insert(order);
        WmsReceiptOrderDetailDO detail = createReceiptOrderDetail(order.getId(), 200L, 100L); receiptOrderDetailMapper.insert(detail);
        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "old", BpmProcessInstanceStatusEnum.REJECT.getStatus(), "revise");
        var reqDetail = createReceiptOrderDetailReqVO(detail.getId(), 200L, "2", "20", "40").setActualQuantity(new BigDecimal("2")).setQualifiedQuantity(BigDecimal.ONE).setUnqualifiedQuantity(BigDecimal.ONE);
        receiptOrderService.updateReceiptOrder(createReceiptOrderSaveReqVO(order.getId(), reqDetail));
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success("retry"));
        assertEquals("retry", receiptOrderService.submitReceiptOrderApproval(1L, order.getId()));
        assertNull(receiptOrderMapper.selectById(order.getId()).getApprovalReason());
        assertNull(receiptOrderMapper.selectById(order.getId()).getApprovalTime());
        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "old", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "stale");
        assertEquals(BpmProcessInstanceStatusEnum.RUNNING.getStatus(), receiptOrderMapper.selectById(order.getId()).getApprovalStatus());
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void inventoryFailureRollsBackApprovalAndExecution() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus()).setProcessInstanceId("failure");
        receiptOrderMapper.insert(order); receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));
        org.mockito.Mockito.doThrow(new IllegalStateException("inventory unavailable")).when(inventoryService).changeInventory(any());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "failure", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "ok"));
        assertEquals(BpmProcessInstanceStatusEnum.RUNNING.getStatus(), receiptOrderMapper.selectById(order.getId()).getApprovalStatus());
        assertEquals(WmsOrderStatusEnum.PREPARE.getStatus(), receiptOrderMapper.selectById(order.getId()).getStatus());
        assertEquals(0, receiptOrderMapper.selectById(order.getId()).getExecutionStatus());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"actualQuantity\":3,\"qualifiedQuantity\":2,\"unqualifiedQuantity\":1,\"qualityRequiredSnapshot\":false}",
        "{\"actualQuantity\":2,\"qualifiedQuantity\":2,\"unqualifiedQuantity\":1,\"qualityRequiredSnapshot\":false}",
        "{\"actualQuantity\":2,\"qualifiedQuantity\":1,\"unqualifiedQuantity\":1,\"qualityRequiredSnapshot\":true}",
        "{\"actualQuantity\":2,\"qualifiedQuantity\":1,\"unqualifiedQuantity\":1}",
        "{\"actualQuantity\":2,\"qualifiedQuantity\":-1,\"unqualifiedQuantity\":3,\"qualityRequiredSnapshot\":false}"
    })
    public void invalidAcceptanceCannotBeSubmitted(String json) {
        WmsReceiptOrderDO order = createReceiptOrder(100L); receiptOrderMapper.insert(order);
        WmsReceiptOrderDetailDO detail = cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(json, WmsReceiptOrderDetailDO.class);
        receiptOrderDetailMapper.insert(detail.setOrderId(order.getId()).setSkuId(200L).setWarehouseId(100L).setQuantity(new BigDecimal("2")));
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success("new-process"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> receiptOrderService.submitReceiptOrderApproval(1L, order.getId()));
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void qualifiedQuantityAloneEntersInventory() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus()).setProcessInstanceId("partial");
        receiptOrderMapper.insert(order);
        WmsReceiptOrderDetailDO detail = cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(
                "{\"skuId\":200,\"warehouseId\":100,\"quantity\":2,\"actualQuantity\":2,\"qualifiedQuantity\":1,\"unqualifiedQuantity\":1,\"qualityRequiredSnapshot\":false,\"price\":20}", WmsReceiptOrderDetailDO.class);
        receiptOrderDetailMapper.insert(detail.setOrderId(order.getId()));
        mockQuality(200L, WmsItemQualityStatusEnum.QUALIFIED.getStatus());
        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "partial", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "ok");
        ArgumentCaptor<WmsInventoryChangeReqDTO> captor = ArgumentCaptor.forClass(WmsInventoryChangeReqDTO.class);
        verify(inventoryService).changeInventory(captor.capture());
        assertEquals(0, BigDecimal.ONE.compareTo(captor.getValue().getItems().get(0).getQuantity()));
        assertEquals(0, new BigDecimal("20").compareTo(captor.getValue().getItems().get(0).getTotalPrice()));
    }

    @Test
    public void missingAcceptanceCannotBeSubmitted() {
        WmsReceiptOrderDO order = createReceiptOrder(100L); receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L).setActualQuantity(null).setQualifiedQuantity(null).setUnqualifiedQuantity(null));
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success("new-process"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> receiptOrderService.submitReceiptOrderApproval(1L, order.getId()));
        assertEquals(BpmProcessInstanceStatusEnum.NOT_START.getStatus(), receiptOrderMapper.selectById(order.getId()).getApprovalStatus());
    }

    @Test
    public void runningApprovalFreezesUpdateCancelAndDelete() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus());
        receiptOrderMapper.insert(order);
        assertServiceException(() -> receiptOrderService.updateReceiptOrder(createReceiptOrderSaveReqVO(order.getId())), cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_APPROVAL_RUNNING);
        assertServiceException(() -> receiptOrderService.cancelReceiptOrder(order.getId()), cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_APPROVAL_RUNNING);
        assertServiceException(() -> receiptOrderService.deleteReceiptOrder(order.getId()), cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_APPROVAL_RUNNING);
    }

    @Test
    public void sourceReceiptCannotChangeWarehouseOrRemoveLines() {
        WmsReceiptOrderDO order = createReceiptOrder(100L).setSourceModule("ERP");
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));
        WmsReceiptOrderSaveReqVO req = createReceiptOrderSaveReqVO(order.getId()); req.setWarehouseId(999L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> receiptOrderService.updateReceiptOrder(req));
    }

    @Test
    public void unapprovedOrderCannotComplete() {
        WmsReceiptOrderDO order = createReceiptOrder(100L);
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class,
                () -> receiptOrderService.completeReceiptOrder(order.getId()));
        assertEquals(WmsOrderStatusEnum.PREPARE.getStatus(), receiptOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    public void approvedOrderExecutesOnceAfterFinalCallback() {
        WmsReceiptOrderDO order = createReceiptOrder(100L)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus())
                .setProcessInstanceId("receipt-process");
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));
        mockQuality(200L, WmsItemQualityStatusEnum.QUALIFIED.getStatus());

        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "receipt-process",
                BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "approved");
        receiptOrderService.updateReceiptOrderApprovalStatus(order.getId(), "receipt-process",
                BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "duplicate");

        WmsReceiptOrderDO saved = receiptOrderMapper.selectById(order.getId());
        assertEquals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), saved.getApprovalStatus());
        assertEquals(WmsOrderStatusEnum.FINISHED.getStatus(), saved.getStatus());
        assertEquals(1, saved.getExecutionStatus());
        verify(inventoryService).changeInventory(any());
    }

    @Test
    public void testCreateReceiptOrder_calculateTotal() {
        // mock 数据
        WmsReceiptOrderSaveReqVO reqVO = createReceiptOrderSaveReqVO(null,
                createReceiptOrderDetailReqVO(null, 2001L, "1.50", "10.00", "16.00"),
                createReceiptOrderDetailReqVO(null, 2002L, "2.50", "20.00", "51.00"));

        // 调用
        Long orderId = receiptOrderService.createReceiptOrder(reqVO);

        // 断言
        WmsReceiptOrderDO dbOrder = receiptOrderMapper.selectById(orderId);
        assertNotNull(dbOrder);
        assertEquals(0, new BigDecimal("4.00").compareTo(dbOrder.getTotalQuantity()));
        assertEquals(0, new BigDecimal("67.00").compareTo(dbOrder.getTotalPrice()));
        List<WmsReceiptOrderDetailDO> details = receiptOrderDetailMapper.selectListByOrderId(orderId);
        assertEquals(2, details.size());
        assertEquals(0, new BigDecimal("16.00").compareTo(details.get(0).getTotalPrice()));
    }

    @Test
    public void testUpdateReceiptOrder_calculateTotal() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L);
        receiptOrderMapper.insert(order);
        WmsReceiptOrderSaveReqVO reqVO = createReceiptOrderSaveReqVO(order.getId(),
                createReceiptOrderDetailReqVO(null, 2001L, "3.00", "30.00", "88.00"));

        // 调用
        receiptOrderService.updateReceiptOrder(reqVO);

        // 断言
        WmsReceiptOrderDO dbOrder = receiptOrderMapper.selectById(order.getId());
        assertNotNull(dbOrder);
        assertEquals(0, new BigDecimal("3.00").compareTo(dbOrder.getTotalQuantity()));
        assertEquals(0, new BigDecimal("88.00").compareTo(dbOrder.getTotalPrice()));
        List<WmsReceiptOrderDetailDO> details = receiptOrderDetailMapper.selectListByOrderId(order.getId());
        assertEquals(1, details.size());
        assertEquals(0, new BigDecimal("88.00").compareTo(details.get(0).getTotalPrice()));
    }

    @Test
    public void testCompleteReceiptOrder_success() {
        // mock 数据
        Long warehouseId = 100L;
        Long skuId = 200L;
        WmsReceiptOrderDO order = createReceiptOrder(warehouseId)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), skuId, warehouseId));
        mockQuality(skuId, WmsItemQualityStatusEnum.QUALIFIED.getStatus());

        // 调用
        receiptOrderService.completeReceiptOrder(order.getId());

        // 断言：入库单
        WmsReceiptOrderDO dbOrder = receiptOrderMapper.selectById(order.getId());
        assertNotNull(dbOrder);
        assertEquals(WmsOrderStatusEnum.FINISHED.getStatus(), dbOrder.getStatus());
        // 断言：库存变更
        ArgumentCaptor<WmsInventoryChangeReqDTO> captor = ArgumentCaptor.forClass(WmsInventoryChangeReqDTO.class);
        verify(inventoryService).changeInventory(captor.capture());
        WmsInventoryChangeReqDTO inventoryReqDTO = captor.getValue();
        assertEquals(order.getId(), inventoryReqDTO.getOrderId());
        assertEquals(order.getNo(), inventoryReqDTO.getOrderNo());
        assertEquals(WmsOrderTypeEnum.RECEIPT.getType(), inventoryReqDTO.getOrderType());
        assertEquals(1, inventoryReqDTO.getItems().size());
        assertEquals(skuId, inventoryReqDTO.getItems().get(0).getSkuId());
        assertEquals(warehouseId, inventoryReqDTO.getItems().get(0).getWarehouseId());
        assertEquals(0, new BigDecimal("2.00").compareTo(inventoryReqDTO.getItems().get(0).getQuantity()));
        assertEquals(0, new BigDecimal("40.00").compareTo(inventoryReqDTO.getItems().get(0).getTotalPrice()));
    }

    @Test
    public void testCompleteReceiptOrder_detailRequired() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        receiptOrderMapper.insert(order);

        // 调用，并断言
        assertServiceException(() -> receiptOrderService.completeReceiptOrder(order.getId()),
                RECEIPT_ORDER_DETAIL_REQUIRED);
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void testCompleteReceiptOrder_duplicateComplete() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));
        mockQuality(200L, WmsItemQualityStatusEnum.QUALIFIED.getStatus());

        // 调用
        receiptOrderService.completeReceiptOrder(order.getId());

        // 调用，并断言：二次完成不能再次写库存
        assertServiceException(() -> receiptOrderService.completeReceiptOrder(order.getId()),
                RECEIPT_ORDER_STATUS_NOT_PREPARE);
        verify(inventoryService).changeInventory(any());
    }

    @Test
    public void testCompleteReceiptOrder_requiredInspectionNeedsImages() {
        WmsReceiptOrderDO order = createReceiptOrder(100L)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L).setQualityRequiredSnapshot(true));
        mockQuality(200L, WmsItemQualityStatusEnum.ABNORMAL.getStatus());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> receiptOrderService.completeReceiptOrder(order.getId()));
        assertEquals(WmsOrderStatusEnum.PREPARE.getStatus(),
                receiptOrderMapper.selectById(order.getId()).getStatus());
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void testCancelReceiptOrder_success() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L);
        receiptOrderMapper.insert(order);

        // 调用
        receiptOrderService.cancelReceiptOrder(order.getId());

        // 断言
        WmsReceiptOrderDO dbOrder = receiptOrderMapper.selectById(order.getId());
        assertNotNull(dbOrder);
        assertEquals(WmsOrderStatusEnum.CANCELED.getStatus(), dbOrder.getStatus());
        verify(inventoryService, never()).changeInventory(any());
    }

    @Test
    public void testUpdateByIdAndStatus_statusNotMatch() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L);
        receiptOrderMapper.insert(order);

        // 调用
        int updateCount = receiptOrderMapper.updateByIdAndStatus(order.getId(),
                WmsOrderStatusEnum.FINISHED.getStatus(),
                new WmsReceiptOrderDO().setStatus(WmsOrderStatusEnum.CANCELED.getStatus()));

        // 断言
        assertEquals(0, updateCount);
        assertEquals(WmsOrderStatusEnum.PREPARE.getStatus(),
                receiptOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    public void testDeleteReceiptOrder_canceled() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L).setStatus(WmsOrderStatusEnum.CANCELED.getStatus());
        receiptOrderMapper.insert(order);
        receiptOrderDetailMapper.insert(createReceiptOrderDetail(order.getId(), 200L, 100L));

        // 调用
        receiptOrderService.deleteReceiptOrder(order.getId());

        // 断言
        assertNull(receiptOrderMapper.selectById(order.getId()));
        assertEquals(0, receiptOrderDetailMapper.selectListByOrderId(order.getId()).size());
    }

    @Test
    public void testDeleteReceiptOrder_finished() {
        // mock 数据
        WmsReceiptOrderDO order = createReceiptOrder(100L).setStatus(WmsOrderStatusEnum.FINISHED.getStatus());
        receiptOrderMapper.insert(order);

        // 调用，并断言
        assertServiceException(() -> receiptOrderService.deleteReceiptOrder(order.getId()),
                RECEIPT_ORDER_STATUS_NOT_DELETABLE);
    }

    private static WmsReceiptOrderDO createReceiptOrder(Long warehouseId) {
        return new WmsReceiptOrderDO()
                .setNo("RK202605120001")
                .setType(WmsReceiptOrderTypeEnum.PURCHASE.getType())
                .setOrderTime(LocalDateTime.of(2026, 5, 12, 0, 0))
                .setStatus(WmsOrderStatusEnum.PREPARE.getStatus())
                .setApprovalStatus(BpmProcessInstanceStatusEnum.NOT_START.getStatus())
                .setExecutionStatus(0)
                .setWarehouseId(warehouseId)
                .setTotalQuantity(new BigDecimal("2.00"))
                .setTotalPrice(new BigDecimal("20.00"));
    }

    private static WmsReceiptOrderSaveReqVO createReceiptOrderSaveReqVO(Long id,
                                                                        WmsReceiptOrderDetailSaveReqVO... details) {
        WmsReceiptOrderSaveReqVO reqVO = new WmsReceiptOrderSaveReqVO();
        reqVO.setId(id);
        reqVO.setNo("RK202605120001");
        reqVO.setType(WmsReceiptOrderTypeEnum.PURCHASE.getType());
        reqVO.setOrderTime(LocalDateTime.of(2026, 5, 12, 0, 0));
        reqVO.setWarehouseId(100L);
        reqVO.setDetails(Arrays.asList(details));
        return reqVO;
    }

    private static WmsReceiptOrderDetailSaveReqVO createReceiptOrderDetailReqVO(Long id, Long skuId,
                                                                                String quantity, String price) {
        return createReceiptOrderDetailReqVO(id, skuId, quantity, price, null);
    }

    private static WmsReceiptOrderDetailSaveReqVO createReceiptOrderDetailReqVO(Long id, Long skuId,
                                                                                String quantity, String price,
                                                                                String totalPrice) {
        WmsReceiptOrderDetailSaveReqVO reqVO = new WmsReceiptOrderDetailSaveReqVO();
        reqVO.setId(id);
        reqVO.setSkuId(skuId);
        reqVO.setQuantity(new BigDecimal(quantity));
        reqVO.setPrice(new BigDecimal(price));
        if (totalPrice != null) {
            reqVO.setTotalPrice(new BigDecimal(totalPrice));
        }
        return reqVO;
    }

    private static WmsReceiptOrderDetailDO createReceiptOrderDetail(Long orderId, Long skuId, Long warehouseId) {
        return WmsReceiptOrderDetailDO.builder()
                .orderId(orderId)
                .skuId(skuId)
                .warehouseId(warehouseId)
                .quantity(new BigDecimal("2.00"))
                .actualQuantity(new BigDecimal("2.00")).qualifiedQuantity(new BigDecimal("2.00")).unqualifiedQuantity(BigDecimal.ZERO).qualityRequiredSnapshot(false)
                .price(new BigDecimal("20.00"))
                .totalPrice(new BigDecimal("40.00"))
                .build();
    }

    private void mockQuality(Long skuId, Integer status) {
        Long itemId = 300L;
        when(itemSkuService.getItemSkuMap(List.of(skuId)))
                .thenReturn(Map.of(skuId, new WmsItemSkuDO().setId(skuId).setItemId(itemId)));
        when(itemService.getItemMap(java.util.Set.of(itemId)))
                .thenReturn(Map.of(itemId, new WmsItemDO().setId(itemId).setName("测试商品")));
        when(qualityReportService.getCurrentQualityReportMap(java.util.Set.of(itemId)))
                .thenReturn(Map.of(itemId, WmsItemQualityReportDO.builder()
                        .id(400L).itemId(itemId).status(status).imageUrls("[]").build()));
    }

}
