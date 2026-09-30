package cn.iocoder.txgy.module.product.service.sku.adapter;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockPageReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 旧 WMS 库存写入契约的失败关闭实现。
 * 不保留任何 WMS 写 SQL；查询统一使用 WMS 服务的租户隔离只读接口。
 */
@Component
@RequiredArgsConstructor
public class ProductWmsInventoryDatabaseAdapter implements ProductWmsInventoryAdapter {
    private final WmsStockReadApi wmsStockReadApi;

    private ServiceException retired() {
        return new ServiceException(400, "商城不直接修改库存；库存以 WMS 实时查询结果为准");
    }

    @Override
    public int getAvailableStock(Long sku, Long warehouse) {
        return getSnapshot(sku, warehouse).availableStock();
    }

    @Override
    public InventorySnapshot getSnapshot(Long sku, Long warehouse) {
        if (sku == null || warehouse == null) return new InventorySnapshot(false, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        WmsStockPageReqDTO request = new WmsStockPageReqDTO();
        request.setSkuId(sku);
        request.setWarehouseId(warehouse);
        request.setPageNo(1);
        request.setPageSize(2);
        final cn.iocoder.txgy.framework.common.pojo.PageResult<cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockRespDTO> page;
        try {
            page = wmsStockReadApi.getStockPage(request).getCheckedData();
        } catch (RuntimeException ex) {
            throw new ServiceException(503, "WMS 库存暂时无法查询，请稍后重试");
        }
        if (page == null || page.getList() == null || page.getList().isEmpty()) {
            return new InventorySnapshot(false, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        if (page.getList().size() != 1) throw new ServiceException(500, "WMS 库存映射不唯一");
        var stock = page.getList().getFirst();
        if (!Objects.equals(stock.getSkuId(), sku) || !Objects.equals(stock.getWarehouseId(), warehouse)) {
            throw new ServiceException(503, "WMS 库存查询结果与商品映射不一致");
        }
        BigDecimal quantity = stock.getQuantity() == null ? BigDecimal.ZERO : stock.getQuantity();
        try {
            return new InventorySnapshot(true, quantity, BigDecimal.ZERO, quantity.intValueExact());
        } catch (ArithmeticException ex) {
            throw new ServiceException(400, "WMS 库存计量单位未配置为商城可用的整数单位");
        }
    }

    @Override
    public Map<String, ReservationSummary> getReservationSummaries(Collection<String> orders) {
        // WMS 预占尚未开放，不从物理库存反推或伪造订单级预占状态。
        return Map.of();
    }
    @Override public void reserve(String order, List<Item> items) { throw retired(); }
    @Override public void release(String order, List<Item> items) { throw retired(); }
    @Override public void outbound(String order, List<Item> items) { throw retired(); }
    @Override public void inboundReturn(String no, String order, List<Item> items) { throw retired(); }
}

