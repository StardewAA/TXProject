package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.stock.ErpStockPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.stock.ErpStockRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.erp.service.integration.wmsread.ErpWmsInventoryReadGateway;
import cn.iocoder.txgy.module.erp.service.integration.wmsread.bo.ErpWmsInventorySnapshotBO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ERP 产品库存列表的 WMS 实物库存只读视图，不写入 ERP 业务账。 */
@Service
public class ErpPhysicalStockViewService {

    @Resource
    private ErpWmsInventoryReadGateway wmsInventoryReadGateway;
    @Resource
    private ErpProductMapper productMapper;
    @Resource
    private ErpWarehouseMapper warehouseMapper;
    @Resource
    private ErpProductService productService;

    public PageResult<ErpStockRespVO> getPage(ErpStockPageReqVO request) {
        ErpProductDO selectedProduct = request.getProductId() == null
                ? null : productMapper.selectById(request.getProductId());
        ErpWarehouseDO selectedWarehouse = request.getWarehouseId() == null
                ? null : warehouseMapper.selectById(request.getWarehouseId());
        List<ErpProductDO> products = request.getProductId() == null
                ? productMapper.selectList(null)
                : selectedProduct == null ? List.of() : List.of(selectedProduct);
        List<ErpWarehouseDO> warehouses = request.getWarehouseId() == null
                ? warehouseMapper.selectList(null)
                : selectedWarehouse == null ? List.of() : List.of(selectedWarehouse);
        Map<String, ErpProductDO> productsByCode = products.stream()
                .filter(p -> p.getMaterialCode() != null && !p.getMaterialCode().isBlank())
                .collect(Collectors.toMap(ErpProductDO::getMaterialCode, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
        Map<String, ErpWarehouseDO> warehousesByCode = warehouses.stream()
                .filter(w -> w.getWmsWarehouseCode() != null && !w.getWmsWarehouseCode().isBlank())
                .collect(Collectors.toMap(ErpWarehouseDO::getWmsWarehouseCode, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
        if (productsByCode.isEmpty() || warehousesByCode.isEmpty()) {
            return PageResult.empty(0L);
        }

        // WMS 返回 SKU × 仓库快照；只展示存在库存记录的组合，并按 ERP 产品汇总多个 SKU。
        List<ErpWmsInventorySnapshotBO> snapshots = wmsInventoryReadGateway.getInventorySnapshot(
                productsByCode.keySet(), warehousesByCode.keySet());
        Map<String, ErpStockRespVO> rowsByKey = new LinkedHashMap<>();
        for (ErpWmsInventorySnapshotBO snapshot : snapshots) {
            ErpProductDO product = productsByCode.get(snapshot.getMaterialCode());
            ErpWarehouseDO warehouse = warehousesByCode.get(snapshot.getWarehouseCode());
            if (product == null || warehouse == null || snapshot.getUpdatedAt() == null) {
                continue;
            }
            String key = product.getId() + ":" + warehouse.getId();
            ErpStockRespVO row = rowsByKey.computeIfAbsent(key, ignored -> new ErpStockRespVO()
                    .setStockKey(key).setProductId(product.getId()).setWarehouseId(warehouse.getId())
                    .setWarehouseName(warehouse.getName()).setCount(BigDecimal.ZERO));
            row.setCount(row.getCount().add(snapshot.getQuantity() == null
                    ? BigDecimal.ZERO : snapshot.getQuantity()));
        }

        if (rowsByKey.isEmpty()) {
            return PageResult.empty(0L);
        }
        Map<Long, ErpProductRespVO> productDetails = productService.getProductVOMap(
                rowsByKey.values().stream().map(ErpStockRespVO::getProductId).collect(Collectors.toSet()));
        rowsByKey.values().forEach(row -> {
            ErpProductRespVO product = productDetails.get(row.getProductId());
            if (product != null) {
                row.setProductName(product.getName()).setCategoryName(product.getCategoryName())
                        .setUnitName(product.getUnitName());
            }
        });
        List<ErpStockRespVO> rows = new ArrayList<>(rowsByKey.values());
        rows.sort(Comparator.comparing(ErpStockRespVO::getProductId).reversed()
                .thenComparing(ErpStockRespVO::getWarehouseId));
        if (PageParam.PAGE_SIZE_NONE.equals(request.getPageSize())) {
            return new PageResult<>(rows, (long) rows.size());
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        if (offset >= rows.size()) {
            return PageResult.empty((long) rows.size());
        }
        int from = (int) offset;
        int to = (int) Math.min(offset + request.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }
}
