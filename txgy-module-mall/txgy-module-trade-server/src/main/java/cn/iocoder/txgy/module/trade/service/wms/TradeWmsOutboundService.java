package cn.iocoder.txgy.module.trade.service.wms;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.wms.TradeWmsShipmentDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsRecoveryMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsShipmentMapper;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.WmsUnitCodeDictionary;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.iocoder.txgy.framework.common.exception.ServiceException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 商城销售订单到 WMS 出库申请的适配层；一张申请严格对应一个店铺和一个仓库。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeWmsOutboundService {

    public static final String PENDING = "WMS_OUTBOUND_PENDING";
    public static final String UNAVAILABLE = "WMS_UNAVAILABLE";
    public static final String REJECTED = "WMS_OUTBOUND_REJECTED";
    public static final String COMPLETED = "WMS_OUTBOUND_COMPLETED";
    public static final String MANUAL_REVIEW = "WMS_MANUAL_REVIEW";

    private final ProductSkuApi productSkuApi;
    private final WmsStockReadApi wmsStockReadApi;
    private final WmsIntegrationApi wmsIntegrationApi;
    private final TradeWmsShipmentMapper shipmentMapper;
    private final TradeWmsRecoveryMapper recoveryMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;

    record GroupKey(long shopId, long warehouseId) {}

    /**
     * 支付成功后创建正式出库申请。远端暂不可用时记录待重试，不回滚已成功的支付事实。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createForPaidOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("创建 WMS 出库申请必须提供有效商城订单编号");
        }
        if (!text(order.getNo()) || orderItems == null || orderItems.isEmpty()
                || orderItems.stream().anyMatch(Objects::isNull)) {
            recordManualReview(order, safe(orderItems), Map.of(), "ORDER_INCOMPLETE：商城订单或明细不完整");
            return;
        }
        Map<Long, ProductSkuRespDTO> skuMap;
        try {
            skuMap = Objects.requireNonNullElse(
                    productSkuApi.getSkuMap(orderItems.stream().map(TradeOrderItemDO::getSkuId).collect(Collectors.toSet())),
                    Map.of());
        } catch (RuntimeException ex) {
            recordManualReview(order, orderItems, Map.of(),
                    "PRODUCT_MAPPING_UNAVAILABLE：读取商品 WMS 映射失败");
            return;
        }
        Map<GroupKey, List<TradeOrderItemDO>> groups = new LinkedHashMap<>();
        boolean invalidMapping = false;
        for (TradeOrderItemDO item : orderItems) {
            ProductSkuRespDTO sku = skuMap.get(item.getSkuId());
            if (sku == null || sku.getWmsSkuId() == null || sku.getWmsWarehouseId() == null) {
                log.error("[createForPaidOrder][订单({}) SKU({})缺少 WMS 映射]", order.getId(), item.getSkuId());
                invalidMapping = true;
                continue;
            }
        }
        if (invalidMapping) {
            recordManualReview(order, orderItems, skuMap,
                    "PRODUCT_MAPPING_INCOMPLETE：订单存在未配置 WMS 映射的 SKU");
            return;
        }
        String ownershipError = shopOwnershipError(order, orderItems);
        if (ownershipError != null) {
            recordManualReview(order, orderItems, skuMap, ownershipError);
            return;
        }
        for (TradeOrderItemDO item : orderItems) {
            ProductSkuRespDTO sku = skuMap.get(item.getSkuId());
            groups.computeIfAbsent(new GroupKey(item.getShopId(), sku.getWmsWarehouseId()),
                    ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<GroupKey, List<TradeOrderItemDO>> entry : groups.entrySet()) {
            createGroup(order, entry.getKey(), entry.getValue(), skuMap);
        }
    }

    private void createGroup(TradeOrderDO order, GroupKey group, List<TradeOrderItemDO> items,
                             Map<Long, ProductSkuRespDTO> productSkus) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        TradeWmsShipmentDO record = shipmentMapper.selectByGroup(order.getId(), group.shopId(), group.warehouseId());
        if (record == null) {
            record = new TradeWmsShipmentDO()
                    .setOrderId(order.getId()).setOrderNo(order.getNo()).setShopId(group.shopId())
                    .setWarehouseId(group.warehouseId()).setSourceOrderNo(sourceOrderNo(order.getId(), group))
                    .setRequestId(requestId(tenantId, order.getId(), group)).setStatus(PENDING)
                    .setEventVersion(1L).setRetryCount(0);
            shipmentMapper.insert(record);
        }
        if (COMPLETED.equals(record.getStatus())) return;
        try {
            WmsOrderRequestDTO request = buildRequest(tenantId, order, group, items, productSkus, record);
            record.setWarehouseCode(request.getItems().getFirst().getWarehouseCode())
                    .setRequestJson(JsonUtils.toJsonString(request));
            send(record, request);
        } catch (RuntimeException ex) {
            markFailure(record, ex);
        }
    }

    WmsOrderRequestDTO buildRequest(long tenantId, TradeOrderDO order, GroupKey group,
                                    List<TradeOrderItemDO> items, Map<Long, ProductSkuRespDTO> productSkus,
                                    TradeWmsShipmentDO record) {
        requireOwnership(tenantId > 0, "商城出库申请缺少有效租户编号");
        requireOwnership(group.shopId() > 0, "商城出库申请缺少有效店铺编号");
        requireOwnership(order.getShopId() != null && order.getShopId() > 0
                        && order.getShopId() == group.shopId(),
                "商城订单与出库申请店铺归属不一致，orderId=" + order.getId());
        requireOwnership(items != null && !items.isEmpty() && items.stream().allMatch(item ->
                        item != null && item.getShopId() != null && item.getShopId() == group.shopId()),
                "商城订单明细与出库申请店铺归属不一致，orderId=" + order.getId());
        Map<Long, WmsStockApplicationOptionsRespDTO.Sku> wmsSkus = safe(wmsStockReadApi.getSkuOptions().getCheckedData()).stream()
                .filter(sku -> sku.getSkuId() != null)
                .collect(Collectors.toMap(WmsStockApplicationOptionsRespDTO.Sku::getSkuId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<Long, WmsWarehouseReadRespDTO> warehouses = safe(wmsStockReadApi.getWarehouses().getCheckedData()).stream()
                .filter(warehouse -> warehouse.getId() != null)
                .collect(Collectors.toMap(WmsWarehouseReadRespDTO::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        WmsWarehouseReadRespDTO warehouse = warehouses.get(group.warehouseId());
        require(warehouse != null && text(warehouse.getCode()), "WMS 仓库映射不存在，warehouseId=" + group.warehouseId());
        require("FINISHED_GOODS".equals(warehouse.getWarehouseType()), "商城销售出库只能使用成品仓，warehouse=" + warehouse.getCode());

        List<TradeOrderItemDO> sorted = items.stream().sorted(Comparator.comparing(TradeOrderItemDO::getId,
                Comparator.nullsLast(Long::compareTo))).toList();
        List<WmsIntegrationDocumentItemDTO> lines = new ArrayList<>(sorted.size());
        int lineNo = 1;
        for (TradeOrderItemDO item : sorted) {
            ProductSkuRespDTO productSku = productSkus.get(item.getSkuId());
            requireOwnership(productSku != null && Objects.equals(group.warehouseId(), productSku.getWmsWarehouseId()),
                    "商城订单明细与出库申请仓库归属不一致，SKU=" + item.getSkuId());
            WmsStockApplicationOptionsRespDTO.Sku wmsSku = productSku == null ? null : wmsSkus.get(productSku.getWmsSkuId());
            require(wmsSku != null, "WMS SKU 主数据不存在，商城SKU=" + item.getSkuId());
            require("FINISHED_GOODS".equals(wmsSku.getMaterialType()), "商城商品必须映射到 WMS 成品物料，SKU=" + item.getSkuId());
            WmsUnitCodeDictionary.Unit mallUnit = WmsUnitCodeDictionary.resolve(findUnit(productSku));
            WmsUnitCodeDictionary.Unit wmsUnit = WmsUnitCodeDictionary.resolve(wmsSku.getUnitName());
            require(mallUnit != null, "商城商品计量单位未纳入单位字典，SKU=" + item.getSkuId());
            require(wmsUnit != null && mallUnit.code().equals(wmsUnit.code()),
                    "商城与 WMS 计量单位不一致，SKU=" + item.getSkuId());
            BigDecimal price = BigDecimal.valueOf(item.getPrice() == null ? 0 : item.getPrice(), 2);
            BigDecimal quantity = BigDecimal.valueOf(item.getCount());
            lines.add(new WmsIntegrationDocumentItemDTO()
                    .setLineNo(lineNo).setSourceLineNo(lineNo++)
                    .setMaterialCode(wmsSku.getMaterialCode()).setSkuCode(wmsSku.getSkuCode())
                    .setMaterialType("FINISHED_GOODS").setMaterialNameSnapshot(item.getSpuName())
                    .setSpecificationSnapshot(specification(item)).setUnitCode(mallUnit.code())
                    .setUnitNameSnapshot(mallUnit.name()).setWarehouseCode(warehouse.getCode())
                    .setPrice(price).setTotalPrice(price.multiply(quantity).setScale(2, RoundingMode.HALF_UP))
                    .setRequiredQuantity(quantity));
        }
        WmsOrderRequestDTO request = new WmsOrderRequestDTO();
        request.setBusinessChainNo(order.getNo());
        request.setSourceOrderNo(record.getSourceOrderNo());
        request.setOrderType("SALE_SHIPMENT");
        request.setEventVersion(record.getEventVersion());
        request.setItems(lines);
        request.setRequestId(record.getRequestId());
        request.setTenantId(tenantId);
        request.setShopId(group.shopId());
        request.setSourceModule("MALL");
        request.setOccurredAt(order.getPayTime() == null ? LocalDateTime.now() : order.getPayTime());
        return request;
    }

    void send(TradeWmsShipmentDO record, WmsOrderRequestDTO request) {
        validateOutboundOwnership(record, request);
        record.setRequestedAt(LocalDateTime.now()).setRetryCount(value(record.getRetryCount()) + 1).setLastError(null);
        var accepted = wmsIntegrationApi.createMallShipment(request).getCheckedData();
        require(accepted != null && Boolean.TRUE.equals(accepted.getAccepted()), "WMS 未受理商城出库申请");
        record.setTargetOrderNo(accepted.getTargetOrderNo()).setStatus(PENDING)
                .setAcceptedAt(LocalDateTime.now()).setLastSyncedAt(LocalDateTime.now());
        shipmentMapper.updateById(record);
    }

    /** 轮询已受理申请，并重试通信失败的幂等请求。 */
    public int retryAndSync(int limit) {
        int handled = 0;
        for (TradeWmsShipmentDO record : shipmentMapper.selectRetryable(limit)) {
            try {
                if (record.getTargetOrderNo() == null || record.getTargetOrderNo().isBlank()) {
                    if (record.getRequestJson() == null || record.getRequestJson().isBlank()) {
                        rebuildAndSend(record);
                    } else {
                        send(record, JsonUtils.parseObject(record.getRequestJson(), WmsOrderRequestDTO.class));
                    }
                } else {
                    requireOwnership(record.getShopId() != null && record.getShopId() > 0,
                            "历史商城出库记录缺少店铺归属，禁止自动重试或查询");
                    sync(record, wmsIntegrationApi.getMallOrderExecution(
                            TenantContextHolder.getRequiredTenantId(), record.getShopId(),
                            record.getSourceOrderNo()).getCheckedData());
                }
            } catch (RuntimeException ex) {
                markFailure(record, ex);
            }
            handled++;
        }
        return handled;
    }

    /**
     * 补偿支付回调在商品 RPC 失败、或商品尚未配置 WMS 映射时没有落下任何出库记录的订单。
     * 查询由 {@link cn.iocoder.txgy.framework.tenant.core.job.TenantJob} 提供的当前租户上下文驱动，
     * SQL 仍显式绑定租户，避免跨租户补偿。
     */
    public int recoverPaidOrdersWithoutShipment(int limit) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<TradeOrderDO> orders = recoveryMapper.selectPaidOrdersWithoutShipment(tenantId, safeLimit);
        int handled = 0;
        for (TradeOrderDO order : orders) {
            try {
                createForPaidOrder(order, orderItemMapper.selectListByOrderId(order.getId()));
            } catch (RuntimeException ex) {
                // 单笔故障不能阻断同租户其它订单；下一次任务仍会命中没有出库记录的订单。
                log.warn("[recoverPaidOrdersWithoutShipment][租户({})订单({})补建 WMS 出库申请失败]",
                        tenantId, order.getId(), ex);
            }
            handled++;
        }
        return handled;
    }

    /** 已经有失败记录但请求体尚未生成时，重新读取当前商品/WMS 主数据后再发送。 */
    private void rebuildAndSend(TradeWmsShipmentDO record) {
        TradeOrderDO order = orderMapper.selectById(record.getOrderId());
        require(order != null, "商城订单不存在，orderId=" + record.getOrderId());
        require(Boolean.TRUE.equals(order.getPayStatus())
                        && TradeOrderStatusEnum.isUndelivered(order.getStatus()),
                "商城订单已不处于已支付待发货状态，orderId=" + record.getOrderId());
        List<TradeOrderItemDO> allItems = orderItemMapper.selectListByOrderId(record.getOrderId());
        Map<Long, ProductSkuRespDTO> productSkus = Objects.requireNonNullElse(
                productSkuApi.getSkuMap(allItems.stream().map(TradeOrderItemDO::getSkuId).collect(Collectors.toSet())),
                Map.of());
        List<TradeOrderItemDO> groupItems = allItems.stream().filter(item -> {
            ProductSkuRespDTO sku = productSkus.get(item.getSkuId());
            return Objects.equals(record.getShopId(), item.getShopId()) && sku != null
                    && Objects.equals(record.getWarehouseId(), sku.getWmsWarehouseId());
        }).toList();
        requireOwnership(record.getShopId() != null && record.getShopId() > 0
                        && order.getShopId() != null && Objects.equals(order.getShopId(), record.getShopId()),
                "历史商城订单或出库记录缺少一致的店铺归属，禁止自动重建");
        require(!groupItems.isEmpty(), "出库分组已无法从订单商品映射恢复，orderId=" + record.getOrderId());
        GroupKey group = new GroupKey(record.getShopId(), record.getWarehouseId());
        WmsOrderRequestDTO request = buildRequest(TenantContextHolder.getRequiredTenantId(), order, group,
                groupItems, productSkus, record);
        record.setWarehouseCode(request.getItems().getFirst().getWarehouseCode())
                .setRequestJson(JsonUtils.toJsonString(request));
        send(record, request);
    }

    /** 商城订单只有在它拆出的全部 WMS 出库申请执行完成后，才允许推进为已发货。 */
    public void validateCompleted(Long orderId) {
        List<TradeWmsShipmentDO> records = shipmentMapper.selectListByOrderId(orderId);
        if (records.isEmpty()) {
            throw new ServiceException(400, "该订单尚未生成 WMS 出库申请，请稍后重试");
        }
        TradeWmsShipmentDO failed = records.stream()
                .filter(record -> REJECTED.equals(record.getStatus()) || UNAVAILABLE.equals(record.getStatus())
                        || MANUAL_REVIEW.equals(record.getStatus()))
                .findFirst().orElse(null);
        if (failed != null) {
            throw new ServiceException(400, "WMS 出库申请未完成：" + Objects.toString(failed.getLastError(), failed.getStatus()));
        }
        long pending = records.stream().filter(record -> !COMPLETED.equals(record.getStatus())).count();
        if (pending > 0) {
            throw new ServiceException(400, "仍有 " + pending + " 张 WMS 出库申请待执行，暂不能确认发货");
        }
    }

    private void sync(TradeWmsShipmentDO record, WmsIntegrationRequestRespDTO result) {
        require(result != null, "WMS 出库申请状态为空");
        Object remoteShopId = result.getResultData() == null ? null : result.getResultData().get("shopId");
        requireOwnership(Objects.equals(record.getShopId(), parseLong(remoteShopId)),
                "WMS 出库单与商城出库申请店铺归属不一致");
        String remote = result.getProcessingStatus();
        String status = switch (Objects.toString(remote, "")) {
            case "SUCCESS", "COMPLETED" -> COMPLETED;
            case "REJECTED", "CANCELLED", "EXECUTION_FAILED" -> REJECTED;
            default -> PENDING;
        };
        record.setStatus(status).setLastError(REJECTED.equals(status) ? "WMS 状态：" + remote : null)
                .setLastSyncedAt(LocalDateTime.now());
        shipmentMapper.updateById(record);
    }

    private void markFailure(TradeWmsShipmentDO record, RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        int retries = record.getRequestedAt() == null ? value(record.getRetryCount()) + 1 : value(record.getRetryCount());
        record.setStatus(ex instanceof ManualReviewException ? MANUAL_REVIEW
                        : record.getTargetOrderNo() == null ? UNAVAILABLE : REJECTED)
                .setLastError(message.substring(0, Math.min(message.length(), 1000)))
                .setLastSyncedAt(LocalDateTime.now()).setRetryCount(retries);
        shipmentMapper.updateById(record);
        log.warn("[markFailure][商城订单({}) WMS出库申请({})处理失败：{}]", record.getOrderId(), record.getSourceOrderNo(), message);
    }

    private static String sourceOrderNo(long orderId, GroupKey group) {
        return "M" + compact(orderId) + "-S" + compact(group.shopId()) + "-W" + compact(group.warehouseId());
    }

    private static String requestId(long tenantId, long orderId, GroupKey group) {
        String source = tenantId + ":" + sourceOrderNo(orderId, group) + ":V1";
        return "MALL-SHIP-" + java.util.UUID.nameUUIDFromBytes(
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void recordManualReview(TradeOrderDO order, List<TradeOrderItemDO> items,
                                    Map<Long, ProductSkuRespDTO> skuMap, String reason) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Long> itemShopIds = safe(items).stream().filter(Objects::nonNull).map(TradeOrderItemDO::getShopId)
                .filter(TradeWmsOutboundService::positive).distinct().toList();
        Long auditShopId = positive(order.getShopId()) ? order.getShopId()
                : itemShopIds.size() == 1 ? itemShopIds.getFirst() : null;
        List<Long> warehouseIds = safe(items).stream().filter(Objects::nonNull)
                .map(TradeOrderItemDO::getSkuId).map(skuMap::get)
                .filter(Objects::nonNull).map(ProductSkuRespDTO::getWmsWarehouseId)
                .filter(Objects::nonNull).distinct().toList();
        if (warehouseIds.isEmpty()) {
            // Warehouse 0 is an explicit audit-only sentinel. MANUAL_REVIEW rows are never retried automatically.
            warehouseIds = List.of(0L);
        }
        for (Long warehouseId : warehouseIds) {
            String sourceOrderNo = "M" + compact(order.getId()) + "-SHOP-ERR-W" + compact(warehouseId);
            TradeWmsShipmentDO record = shipmentMapper.selectBySourceOrderNo(sourceOrderNo);
            if (record == null) {
                record = new TradeWmsShipmentDO().setOrderId(order.getId())
                        .setOrderNo(text(order.getNo()) ? order.getNo() : "UNKNOWN-" + compact(order.getId()))
                        .setShopId(auditShopId).setWarehouseId(warehouseId).setSourceOrderNo(sourceOrderNo)
                        .setRequestId(requestId(tenantId, sourceOrderNo)).setStatus(MANUAL_REVIEW)
                        .setEventVersion(1L).setRetryCount(0).setLastError(reason)
                        .setLastSyncedAt(LocalDateTime.now());
                shipmentMapper.insert(record);
            } else {
                record.setStatus(MANUAL_REVIEW).setLastError(reason).setLastSyncedAt(LocalDateTime.now());
                shipmentMapper.updateById(record);
            }
        }
        log.error("[createForPaidOrder][订单({})无法自动创建正式出库，已落人工复核记录：{}]", order.getId(), reason);
    }

    private static String shopOwnershipError(TradeOrderDO order, List<TradeOrderItemDO> items) {
        if (!positive(order.getShopId())) return "商城订单缺少有效店铺编号";
        if (items.stream().anyMatch(item -> item == null || !positive(item.getShopId()))) {
            return "商城订单明细缺少有效店铺编号";
        }
        if (items.stream().anyMatch(item -> !Objects.equals(order.getShopId(), item.getShopId()))) {
            return "商城订单与明细店铺归属不一致";
        }
        return null;
    }

    private static void validateOutboundOwnership(TradeWmsShipmentDO record, WmsOrderRequestDTO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        requireOwnership(record != null && request != null, "商城出库记录或请求快照缺失");
        requireOwnership(Objects.equals(request.getTenantId(), tenantId), "商城出库请求租户与当前上下文不一致");
        requireOwnership(positive(record.getShopId()) && Objects.equals(record.getShopId(), request.getShopId()),
                "商城出库记录与请求快照店铺归属不一致");
        requireOwnership(record.getWarehouseId() != null && record.getWarehouseId() > 0,
                "商城出库记录缺少有效仓库编号");
        requireOwnership(request.getItems() != null && !request.getItems().isEmpty(), "商城出库请求明细为空");
        var warehouseCodes = request.getItems().stream().map(WmsIntegrationDocumentItemDTO::getWarehouseCode)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        requireOwnership(warehouseCodes.size() == 1 && (record.getWarehouseCode() == null
                        || warehouseCodes.contains(record.getWarehouseCode())),
                "商城出库请求必须且只能对应记录中的单一仓库");
    }

    private static String requestId(long tenantId, String sourceOrderNo) {
        return "MALL-SHIP-" + java.util.UUID.nameUUIDFromBytes(
                (tenantId + ":" + sourceOrderNo + ":V1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void requireOwnership(boolean condition, String message) {
        if (!condition) throw new ManualReviewException(message);
    }

    private static final class ManualReviewException extends IllegalStateException {
        private ManualReviewException(String message) {
            super(message);
        }
    }

    /** base36 保留唯一性，同时确保雪花 ID 组合不超过 WMS 单号长度。 */
    private static String compact(long value) {
        return Long.toUnsignedString(value, 36).toUpperCase(java.util.Locale.ROOT);
    }

    private static String findUnit(ProductSkuRespDTO sku) {
        if (sku == null || sku.getProperties() == null) return null;
        return sku.getProperties().stream().filter(Objects::nonNull)
                .filter(property -> "单位".equals(property.getPropertyName()) || "计量单位".equals(property.getPropertyName()))
                .map(ProductPropertyValueDetailRespDTO::getValueName).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static String specification(TradeOrderItemDO item) {
        if (item.getProperties() == null) return null;
        String value = item.getProperties().stream().filter(Objects::nonNull)
                .map(property -> property.getPropertyName() + "：" + property.getValueName())
                .collect(Collectors.joining("；"));
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private static <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
