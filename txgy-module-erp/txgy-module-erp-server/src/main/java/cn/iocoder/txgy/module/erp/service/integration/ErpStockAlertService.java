package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductUnitMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_STATE_STALE;

@Service
@Slf4j
public class ErpStockAlertService {

    @Resource private ErpProductMapper productMapper;
    @Resource private ErpStockMapper stockMapper;
    @Resource private ErpWarehouseMapper warehouseMapper;
    @Resource private ErpProductUnitMapper productUnitMapper;
    @Resource private ErpWmsCollaborationBMapper collaborationMapper;
    @Resource private ErpWmsCollaborationBService collaborationBService;

    /** 扫描所有配置了安全库存的原材料，低于阈值则生成协同单。需在租户上下文中调用。 */
    public int scanAndAlert() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<ErpProductDO> products = productMapper.selectList(
                new LambdaQueryWrapper<ErpProductDO>()
                        .eq(ErpProductDO::getMaterialType, "RAW_MATERIAL")
                        .isNotNull(ErpProductDO::getSafetyStock)
                        .eq(ErpProductDO::getStatus, 0)); // 0=启用
        log.info("Stock alert scan: {} raw materials with safety stock (tenant={})", products.size(), tenantId);
        Map<Long, ErpWarehouseDO> warehouseMap = loadWarehouseMap();
        int created = 0;
        for (ErpProductDO product : products) {
            try {
                List<ErpStockDO> stockRows = queryStockRows(product.getId());
                BigDecimal totalStock = sumStock(stockRows);
                String severity = evaluateSeverity(product, totalStock);
                if (severity == null) {
                    continue; // 库存充足，不生成协同单
                }
                if (hasOpenAlert(product.getMaterialCode())) {
                    // 同物料已有未结案（待确认/待补充/生成中）的缺货预警单，不再按天堆积重复单
                    log.debug("Open stock alert already exists for {}, skip", product.getMaterialCode());
                    continue;
                }
                ErpCollaborationBReceiveReqDTO dto = buildAlertDTO(product, stockRows, warehouseMap, totalStock, severity, tenantId);
                collaborationBService.receive(dto);
                created++;
            } catch (ServiceException e) {
                // 协同单号同日重复时 receive 抛 WMS_CALLBACK_STATE_STALE，视为今日已生成（幂等跳过）
                if (e.getCode() != null && e.getCode().equals(WMS_CALLBACK_STATE_STALE.getCode())) {
                    log.debug("Stock alert for {} already exists today, skip", product.getMaterialCode());
                } else {
                    log.warn("Stock alert generation failed for {}: code={} msg={}",
                            product.getMaterialCode(), e.getCode(), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Stock alert generation failed for {}: {}", product.getMaterialCode(), e.toString(), e);
            }
        }
        return created;
    }

    /** 库存预警概览（含充足/不足/严重不足），供前端查看。 */
    public List<Map<String, Object>> getOverview() {
        List<ErpProductDO> products = productMapper.selectList(
                new LambdaQueryWrapper<ErpProductDO>()
                        .eq(ErpProductDO::getMaterialType, "RAW_MATERIAL")
                        .eq(ErpProductDO::getStatus, 0));
        Map<Long, ErpWarehouseDO> warehouseMap = loadWarehouseMap();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ErpProductDO product : products) {
            List<ErpStockDO> stockRows = queryStockRows(product.getId());
            BigDecimal total = sumStock(stockRows);
            List<Map<String, Object>> perWarehouse = new ArrayList<>();
            for (ErpStockDO row : stockRows) {
                Map<String, Object> wh = new LinkedHashMap<>();
                ErpWarehouseDO warehouse = warehouseMap.get(row.getWarehouseId());
                wh.put("warehouseCode", warehouse != null && warehouse.getWmsWarehouseCode() != null
                        ? warehouse.getWmsWarehouseCode() : String.valueOf(row.getWarehouseId()));
                wh.put("warehouseName", warehouse != null ? warehouse.getName() : "");
                wh.put("quantity", row.getCount() != null ? row.getCount() : BigDecimal.ZERO);
                perWarehouse.add(wh);
            }
            result.add(toOverview(product, total, perWarehouse));
        }
        return result;
    }

    private Map<Long, ErpWarehouseDO> loadWarehouseMap() {
        Map<Long, ErpWarehouseDO> map = new HashMap<>();
        for (ErpWarehouseDO warehouse : warehouseMapper.selectList()) {
            map.put(warehouse.getId(), warehouse);
        }
        return map;
    }

    /**
     * 当前库存口径：ERP 业务库存 erp_stock（采购入库 / 其它出库 / 销售出库 / 调拨都会更新这本账）。
     * 不读 WMS 实物库存 —— ERP 的其它出库不会写 WMS，读 WMS 会导致出库后余量不变化、无法预警。
     */
    private List<ErpStockDO> queryStockRows(Long productId) {
        List<ErpStockDO> rows = stockMapper.selectList(
                new LambdaQueryWrapper<ErpStockDO>().eq(ErpStockDO::getProductId, productId));
        return rows != null ? rows : List.of();
    }

    private BigDecimal sumStock(List<ErpStockDO> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (ErpStockDO row : rows) {
            if (row.getCount() != null) {
                total = total.add(row.getCount());
            }
        }
        return total;
    }

    /** 该物料是否已存在未结案的库存预警协同单（单号前缀 STOCK-ALERT-{code}-）。 */
    private boolean hasOpenAlert(String materialCode) {
        Long count = collaborationMapper.selectCount(
                new LambdaQueryWrapper<ErpWmsCollaborationBDO>()
                        .likeRight(ErpWmsCollaborationBDO::getCollaborationNo,
                                "STOCK-ALERT-" + materialCode + "-")
                        .in(ErpWmsCollaborationBDO::getBusinessStatus,
                                "PENDING_CONFIRMATION", "NEED_SUPPLEMENT", "CONFIRMING"));
        return count != null && count > 0;
    }

    private String evaluateSeverity(ErpProductDO product, BigDecimal totalStock) {
        BigDecimal safety = product.getSafetyStock();
        if (safety == null) {
            return null; // 未配置阈值不参与预警
        }
        BigDecimal critical = product.getCriticalStock();
        if (critical != null && totalStock.compareTo(critical) < 0) {
            return "CRITICAL"; // 严重不足
        }
        if (totalStock.compareTo(safety) < 0) {
            return "INSUFFICIENT"; // 不足
        }
        return null; // 充足
    }

    private ErpCollaborationBReceiveReqDTO buildAlertDTO(ErpProductDO product, List<ErpStockDO> stockRows,
                                                          Map<Long, ErpWarehouseDO> warehouseMap,
                                                          BigDecimal totalStock, String severity, Long tenantId) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String code = product.getMaterialCode();
        String collaborationNo = "STOCK-ALERT-" + code + "-" + today; // 同日同物料幂等
        // SKU/单位快照：SKU 沿用 WMS 统一编码约定「物料编码-DEFAULT」；单位取产品主单位
        String skuCode = code + "-DEFAULT";
        String unitCode = code;
        String unitName = code;
        if (product.getUnitId() != null) {
            ErpProductUnitDO unit = productUnitMapper.selectById(product.getUnitId());
            if (unit != null) {
                if (unit.getCode() != null && !unit.getCode().isBlank()) {
                    unitCode = unit.getCode();
                }
                if (unit.getName() != null && !unit.getName().isBlank()) {
                    unitName = unit.getName();
                }
            }
        }
        BigDecimal shortage = product.getSafetyStock().subtract(totalStock).max(BigDecimal.ZERO);
        ErpWmsDocumentItemDTO item = new ErpWmsDocumentItemDTO();
        item.setLineNo(1);
        item.setSourceLineNo(1);
        item.setMaterialCode(code);
        item.setSkuCode(skuCode);
        item.setMaterialType(product.getMaterialType());
        item.setMaterialNameSnapshot(product.getName());
        // 规格/参考价取产品主数据快照，避免协同单明细出现空白列
        item.setSpecificationSnapshot(product.getStandard());
        item.setUnitCode(unitCode);
        item.setUnitNameSnapshot(unitName);
        item.setShortageQuantity(shortage);
        item.setRequiredQuantity(shortage);
        item.setReferencePrice(product.getPurchasePrice());

        ErpCollaborationBReceiveReqDTO dto = new ErpCollaborationBReceiveReqDTO();
        dto.setRequestId("STOCK-" + UUID.randomUUID());
        dto.setTenantId(tenantId);
        dto.setBusinessChainNo("BC-" + collaborationNo);
        dto.setEventVersion(1L);
        dto.setOccurredAt(LocalDateTime.now());
        dto.setCollaborationNo(collaborationNo);
        dto.setCollaborationType("SHORTAGE_COLLABORATION");
        dto.setSourceModule("ERP");
        dto.setSourceOrderNo("STOCK-SCAN-" + today);
        dto.setSeverity(severity);
        dto.setCurrency("CNY"); // 内部库存预警统一人民币，参考价才有币种语义
        dto.setItems(List.of(item));
        return dto;
    }

    private Map<String, Object> toOverview(ErpProductDO p, BigDecimal total, List<Map<String, Object>> perWarehouse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", p.getId());
        m.put("materialCode", p.getMaterialCode());
        m.put("name", p.getName());
        m.put("totalStock", total);
        m.put("safetyStock", p.getSafetyStock() != null ? p.getSafetyStock() : BigDecimal.ZERO);
        m.put("criticalStock", p.getCriticalStock() != null ? p.getCriticalStock() : BigDecimal.ZERO);
        m.put("severity", evaluateSeverity(p, total) != null ? evaluateSeverity(p, total) : "SUFFICIENT");
        m.put("perWarehouse", perWarehouse);
        return m;
    }
}
