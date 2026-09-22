package cn.iocoder.txgy.module.trade.service.erp;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Item;

/**
 * 切换 WMS 直连前的商城 ERP 履约历史，只允许查询。
 *
 * <p>新订单不得通过本服务创建、重试或回传 ERP 请求；历史表保留用于订单审计。</p>
 */
@Service
@Transactional(readOnly = true)
public class TradeErpInventoryService {

    private final JdbcTemplate jdbc;

    public TradeErpInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long tenant() {
        Long id = TenantContextHolder.getRequiredTenantId();
        if (id <= 0) throw new ServiceException(403, "历史履约记录必须属于有效租户");
        return id;
    }

    public List<Entry> list(Long orderId) {
        return jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id=? ORDER BY create_time,id",
                this::map, tenant(), orderId);
    }

    public Map<Long, List<Entry>> listByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        if (orderIds.size() > 200) throw new ServiceException(400, "每批最多查询 200 个订单的历史履约记录");
        var args = new ArrayList<Object>();
        args.add(tenant());
        args.addAll(orderIds);
        var rows = jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id IN ("
                        + String.join(",", Collections.nCopies(orderIds.size(), "?")) + ") ORDER BY create_time,id",
                this::map, args.toArray());
        Map<Long, List<Entry>> grouped = new HashMap<>();
        for (var row : rows) grouped.computeIfAbsent(row.orderId(), key -> new ArrayList<>()).add(row);
        return grouped;
    }

    public Entry get(String requestId) {
        var rows = jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE id=? AND tenant_id=?",
                this::map, requestId, tenant());
        if (rows.isEmpty()) throw new ServiceException(400, "历史履约记录不存在或无权访问");
        return rows.getFirst();
    }

    private Entry map(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getString("id"), rs.getLong("order_id"), rs.getString("order_no"),
                rs.getString("operation"), rs.getString("business_key"), rs.getString("mode"),
                rs.getString("status"), rs.getString("message"), rs.getInt("attempts"),
                rs.getString("erp_no"), rs.getObject("logistics_id", Long.class), rs.getString("logistics_no"),
                JsonUtils.parseArray(rs.getString("payload"), Item.class),
                rs.getTimestamp("update_time").toLocalDateTime());
    }
}
