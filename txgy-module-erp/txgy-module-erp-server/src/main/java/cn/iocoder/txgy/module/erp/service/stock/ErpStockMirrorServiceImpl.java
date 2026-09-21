package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockReconciliationRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockUnmappedRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockMirrorDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWmsInventorySnapshotDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMirrorMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWmsInventoryReadonlyMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ERP 实物库存镜像 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class ErpStockMirrorServiceImpl implements ErpStockMirrorService {

    @Resource
    private ErpStockMirrorMapper mirrorMapper;
    @Resource
    private ErpWmsInventoryReadonlyMapper wmsReadonlyMapper;
    @Resource
    private ErpStockMapper stockMapper;
    @Resource
    private ErpProductMapper productMapper;
    @Resource
    private ErpWarehouseMapper warehouseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncMirror() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        // 1. 读取 WMS 实物库存快照（只读，按映射链关联）
        List<ErpWmsInventorySnapshotDO> snapshots = wmsReadonlyMapper.selectInventorySnapshot(tenantId);
        if (snapshots.isEmpty()) {
            log.warn("[syncMirror][租户 {} 未取到任何实物库存快照] 请检查仓库与物料的 WMS 映射是否已建立", tenantId);
            return 0;
        }
        // 2. 逐项 upsert 到实物账镜像
        LocalDateTime now = LocalDateTime.now();
        for (ErpWmsInventorySnapshotDO snapshot : snapshots) {
            BigDecimal quantity = snapshot.getQuantity() != null ? snapshot.getQuantity() : BigDecimal.ZERO;
            ErpStockMirrorDO existing = mirrorMapper.selectByProductAndWarehouse(
                    snapshot.getProductId(), snapshot.getWarehouseId());
            if (existing == null) {
                mirrorMapper.insert(ErpStockMirrorDO.builder()
                        .productId(snapshot.getProductId()).warehouseId(snapshot.getWarehouseId())
                        .count(quantity).wmsSkuId(snapshot.getWmsSkuId())
                        .wmsWarehouseId(snapshot.getWmsWarehouseId()).syncTime(now).build());
            } else {
                mirrorMapper.updateById(new ErpStockMirrorDO().setId(existing.getId())
                        .setCount(quantity).setWmsSkuId(snapshot.getWmsSkuId())
                        .setWmsWarehouseId(snapshot.getWmsWarehouseId()).setSyncTime(now));
            }
        }
        log.info("[syncMirror][租户 {} 同步实物库存 {} 条]", tenantId, snapshots.size());
        return snapshots.size();
    }

    @Override
    public List<ErpStockReconciliationRespVO> getReconciliation() {
        // 1. 业务账（ERP 单据推演）
        Map<String, ErpStockDO> businessMap = stockMapper.selectList(null).stream()
                .collect(Collectors.toMap(s -> key(s.getProductId(), s.getWarehouseId()), Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        // 2. 实物账（WMS 同步）
        Map<String, ErpStockMirrorDO> mirrorMap = mirrorMapper.selectListAll().stream()
                .collect(Collectors.toMap(m -> key(m.getProductId(), m.getWarehouseId()), Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        // 3. 取并集，逐项比对
        Set<String> keys = new LinkedHashSet<>(businessMap.keySet());
        keys.addAll(mirrorMap.keySet());
        // 4. 批量取名称
        Set<Long> productIds = new HashSet<>();
        Set<Long> warehouseIds = new HashSet<>();
        keys.forEach(k -> {
            String[] parts = k.split(":");
            productIds.add(Long.valueOf(parts[0]));
            warehouseIds.add(Long.valueOf(parts[1]));
        });
        Map<Long, ErpProductDO> productNameMap = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(ErpProductDO::getId, Function.identity(), (a, b) -> a));
        Map<Long, ErpWarehouseDO> warehouseNameMap = warehouseIds.isEmpty() ? Collections.emptyMap()
                : warehouseMapper.selectBatchIds(warehouseIds).stream()
                        .collect(Collectors.toMap(ErpWarehouseDO::getId, Function.identity(), (a, b) -> a));
        // 5. 组装
        List<ErpStockReconciliationRespVO> result = new ArrayList<>(keys.size());
        for (String k : keys) {
            String[] parts = k.split(":");
            Long productId = Long.valueOf(parts[0]);
            Long warehouseId = Long.valueOf(parts[1]);
            ErpStockDO stock = businessMap.get(k);
            ErpStockMirrorDO mirror = mirrorMap.get(k);
            BigDecimal businessCount = stock != null && stock.getCount() != null ? stock.getCount() : BigDecimal.ZERO;
            BigDecimal mirrorCount = mirror != null && mirror.getCount() != null ? mirror.getCount() : BigDecimal.ZERO;
            ErpProductDO product = productNameMap.get(productId);
            ErpWarehouseDO warehouse = warehouseNameMap.get(warehouseId);
            result.add(new ErpStockReconciliationRespVO()
                    .setProductId(productId).setWarehouseId(warehouseId)
                    .setProductCode(product != null ? product.getMaterialCode() : null)
                    .setProductName(product != null ? product.getName() : null)
                    .setWarehouseName(warehouse != null ? warehouse.getName() : null)
                    .setBusinessCount(businessCount).setMirrorCount(mirrorCount)
                    .setDiff(businessCount.subtract(mirrorCount))
                    .setMirrorSyncTime(mirror != null ? mirror.getSyncTime() : null));
        }
        return result;
    }

    @Override
    public ErpStockUnmappedRespVO getUnmappedDiagnostics() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<String> unmappedWarehouses = wmsReadonlyMapper.selectUnmappedWarehouseCodes(tenantId);
        List<String> unmappedProducts = wmsReadonlyMapper.selectUnmappedProductCodes(tenantId);
        return new ErpStockUnmappedRespVO()
                .setUnmappedWarehouseCodes(unmappedWarehouses)
                .setUnmappedProductCodes(unmappedProducts)
                .setSyncReady(unmappedWarehouses.isEmpty() && unmappedProducts.isEmpty());
    }

    private static String key(Long productId, Long warehouseId) {
        return productId + ":" + warehouseId;
    }

}
