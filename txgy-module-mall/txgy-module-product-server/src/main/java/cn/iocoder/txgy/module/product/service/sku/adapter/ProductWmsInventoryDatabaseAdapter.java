package cn.iocoder.txgy.module.product.service.sku.adapter;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 旧 WMS 库存写入契约的失败关闭实现。
 * 不保留任何 WMS 写 SQL；查询改用只读摘要，库存变更由 trade 的 ERP 适配层处理。
 */
@Component
public class ProductWmsInventoryDatabaseAdapter implements ProductWmsInventoryAdapter {
    private ServiceException retired() {
        return new ServiceException(400, "WMS仅供只读查询；库存变更和履约状态请使用商城ERP适配入口");
    }
    @Override public int getAvailableStock(Long sku, Long warehouse) { throw retired(); }
    @Override public InventorySnapshot getSnapshot(Long sku, Long warehouse) { throw retired(); }
    @Override public Map<String, ReservationSummary> getReservationSummaries(Collection<String> orders) { throw retired(); }
    @Override public void reserve(String order, List<Item> items) { throw retired(); }
    @Override public void release(String order, List<Item> items) { throw retired(); }
    @Override public void outbound(String order, List<Item> items) { throw retired(); }
    @Override public void inboundReturn(String no, String order, List<Item> items) { throw retired(); }
}

