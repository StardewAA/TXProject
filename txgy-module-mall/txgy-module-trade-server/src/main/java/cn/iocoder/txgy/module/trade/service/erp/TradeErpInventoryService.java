package cn.iocoder.txgy.module.trade.service.erp;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;

/**
 * 唯一的商城库存履约入口。只写 trade_erp_*，不写 WMS 或 product_sku 库存。
 * 与商城订单使用同一事务；请求以 tenant/order/operation/businessKey 唯一。
 * pending 是待对接契约，不代表已发送至真实 ERP。
 */
@Service
public class TradeErpInventoryService {
    @jakarta.annotation.Resource
    private cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchJournal workbenchJournal;
    private void workbenchChanged(Long id) { if(workbenchJournal!=null)workbenchJournal.changed(id); }
    private final JdbcTemplate jdbc;
    private final ErpInventoryProperties properties;
    private final Environment environment;

    public TradeErpInventoryService(JdbcTemplate jdbc, ErpInventoryProperties properties, Environment environment) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (!Set.of("pending", "mock").contains(properties.getMode())) throw invalid("ERP 模式只能为 pending 或 mock");
        if ("mock".equals(properties.getMode())) requireMock();
        if (properties.isCallbackEnabled()
                && (properties.getCallbackSecret() == null || properties.getCallbackSecret().length() < 32)) {
            throw invalid("ERP 回传启用时必须配置至少32位独立密钥");
        }
    }

    public void requireMock() {
        Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
        Set<String> allowed = Set.of("local", "dev", "test", "unit-test");
        if (!"mock".equals(properties.getMode()) || !properties.isMockEnabled() || profiles.isEmpty()
                || !allowed.containsAll(profiles)) {
            throw new ServiceException(403, "模拟 ERP 仅允许在明确开启的 local/dev/test 环境使用");
        }
    }

    public static ServiceException invalid(String message) { return new ServiceException(400, message); }
    private long tenant() {
        Long id = TenantContextHolder.getRequiredTenantId();
        if (id <= 0) throw new ServiceException(403, "ERP 库存操作必须属于有效企业租户");
        return id;
    }

    private void lockOrder(Long orderId) {
        if (jdbc.queryForList("SELECT id FROM trade_order WHERE id=? AND tenant_id=? AND deleted=0 FOR UPDATE",
                Long.class, orderId, tenant()).isEmpty()) throw invalid("订单不存在或不属于当前租户");
    }

    @Transactional(rollbackFor = Exception.class)
    public Entry request(TradeOrderDO order, Operation operation, String businessKey,
                         List<TradeOrderItemDO> orderItems, Long logisticsId, String logisticsNo) {
        validateConfiguration();
        lockOrder(order.getId());
        if (businessKey == null || businessKey.isBlank() || businessKey.length() > 64) throw invalid("业务编号无效");
        Entry reservation = reservation(order.getId());
        List<Item> items = snapshot(orderItems, reservation);
        String payload = JsonUtils.toJsonString(items);
        var existing = jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id=?"
                        + " AND operation=? AND business_key=?", this::map, tenant(), order.getId(), operation.name(), businessKey);
        if (!existing.isEmpty()) {
            Entry entry = existing.getFirst();
            if (!entry.items().equals(items) || !Objects.equals(entry.logisticsId(), logisticsId)
                    || !Objects.equals(entry.logisticsNo(), logisticsNo)) throw invalid("同一 ERP 业务请求不能变更商品或物流参数");
            return entry;
        }
        String mode = reservation == null
                ? (operation == Operation.RESERVE ? properties.getMode() : "pending") : reservation.mode();
        // 不允许切换配置后将已有正式请求改成模拟成功。
        if (reservation != null && !mode.equals(properties.getMode()))
            throw invalid("订单 ERP 模式与当前配置不一致，请先核实历史请求");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO trade_erp_inventory_request"
                        + " (id,tenant_id,order_id,order_no,operation,business_key,mode,status,payload,logistics_id,logistics_no,message)"
                        + " VALUES (?,?,?,?,?,?,?,'PENDING',?,?,?,?)",
                id, tenant(), order.getId(), order.getNo(), operation.name(), businessKey, mode, payload,
                logisticsId, logisticsNo, "待 ERP 确认；当前尚未接通真实 ERP，不代表库存已变更");
        if ("mock".equals(mode)) {
            requireMock();
            applyMock(order.getId(), operation, items);
            jdbc.update("UPDATE trade_erp_inventory_request SET status='CONFIRMED',erp_no=?,message=?,update_time=CURRENT_TIMESTAMP"
                    + " WHERE id=? AND tenant_id=?", "MOCK-" + id, "模拟 ERP 已确认，仅影响独立测试账本，不代表真实出入库", id, tenant());
        }
        workbenchChanged(order.getId());
        return get(id);
    }

    private List<Item> snapshot(List<TradeOrderItemDO> orderItems, Entry reservation) {
        if (orderItems == null || orderItems.isEmpty() || orderItems.size() > 200) throw invalid("ERP 请求商品明细不能为空或超过200行");
        SortedMap<Long, Integer> counts = new TreeMap<>();
        for (TradeOrderItemDO item : orderItems) {
            if (item.getSkuId() == null || item.getCount() == null || item.getCount() <= 0) throw invalid("商品和数量无效");
            counts.merge(item.getSkuId(), item.getCount(), Math::addExact);
        }
        List<Item> items = new ArrayList<>();
        counts.forEach((sku, count) -> {
            if (reservation != null) {
                Item original = reservation.items().stream().filter(item -> item.skuId().equals(sku)).findFirst()
                        .orElseThrow(() -> invalid("商品不属于原订单ERP预占明细"));
                items.add(new Item(sku, count, original.wmsSkuId(), original.wmsWarehouseId()));
                return;
            }
            var rows = jdbc.query("SELECT wms_sku_id,wms_warehouse_id FROM product_sku WHERE id=? AND tenant_id=?",
                    (rs, n) -> new Item(sku, count, rs.getObject(1, Long.class), rs.getObject(2, Long.class)), sku, tenant());
            if (rows.isEmpty()) throw invalid("商品规格不存在或不属于当前租户");
            items.add(rows.getFirst());
        });
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public void seed(Seed seed) {
        requireMock();
        if (seed.skuId() == null || seed.skuId() <= 0 || seed.quantity() < 0 || seed.quantity() > 100000000)
            throw invalid("模拟库存参数无效");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM product_sku WHERE id=? AND tenant_id=? AND deleted=0",
                Integer.class, seed.skuId(), tenant()) == 0) throw invalid("商品规格不存在");
        var rows = jdbc.queryForList("SELECT initial_quantity FROM trade_erp_mock_stock WHERE tenant_id=? AND sku_id=? FOR UPDATE",
                Integer.class, tenant(), seed.skuId());
        if (!rows.isEmpty()) {
            if (rows.getFirst() != seed.quantity()) throw invalid("模拟库存已初始化，不能重复覆盖或充值");
            return; // 重复初始化不重置已经消耗的模拟库存。
        }
        jdbc.update("INSERT INTO trade_erp_mock_stock (tenant_id,sku_id,initial_quantity,quantity,reserved) VALUES (?,?,?,?,0)",
                tenant(), seed.skuId(), seed.quantity(), seed.quantity());
    }

    private void applyMock(Long orderId, Operation operation, List<Item> items) {
        // 所有 SKU 按编号加锁，整笔原子提交；异常回滚请求与模拟账本。
        for (Item item : items) {
            var stock = jdbc.queryForList("SELECT quantity,reserved FROM trade_erp_mock_stock"
                    + " WHERE tenant_id=? AND sku_id=? FOR UPDATE", tenant(), item.skuId());
            if (stock.isEmpty()) throw invalid("请先初始化该商品的独立 ERP 模拟库存，SKU=" + item.skuId());
            var balances = jdbc.queryForList("SELECT reserved,released,outbound,returned FROM trade_erp_mock_order_stock"
                    + " WHERE tenant_id=? AND order_id=? AND sku_id=? FOR UPDATE", tenant(), orderId, item.skuId());
            int reserved = 0, released = 0, outbound = 0, returned = 0;
            if (!balances.isEmpty()) {
                var b = balances.getFirst();
                reserved = number(b, "reserved"); released = number(b, "released");
                outbound = number(b, "outbound"); returned = number(b, "returned");
            }
            int count = item.count();
            switch (operation) {
                case RESERVE -> {
                    if (!balances.isEmpty()) throw invalid("同一订单商品不能重复建立预占");
                    if (number(stock.getFirst(), "quantity") - number(stock.getFirst(), "reserved") < count)
                        throw invalid("ERP 模拟可用库存不足，SKU=" + item.skuId());
                    jdbc.update("UPDATE trade_erp_mock_stock SET reserved=reserved+? WHERE tenant_id=? AND sku_id=?",
                            count, tenant(), item.skuId());
                    jdbc.update("INSERT INTO trade_erp_mock_order_stock (tenant_id,order_id,sku_id,reserved) VALUES (?,?,?,?)",
                            tenant(), orderId, item.skuId(), count);
                }
                case RELEASE, OUTBOUND -> {
                    if (reserved - released - outbound < count) throw invalid("未预占或可处理数量不足；历史订单需先由 ERP 核实");
                    jdbc.update("UPDATE trade_erp_mock_stock SET reserved=reserved-?,quantity=quantity-?"
                            + " WHERE tenant_id=? AND sku_id=?", count, operation == Operation.OUTBOUND ? count : 0, tenant(), item.skuId());
                    String column = operation == Operation.OUTBOUND ? "outbound" : "released";
                    jdbc.update("UPDATE trade_erp_mock_order_stock SET " + column + "=" + column
                            + "+? WHERE tenant_id=? AND order_id=? AND sku_id=?", count, tenant(), orderId, item.skuId());
                }
                case RETURN -> {
                    if (outbound - returned < count) throw invalid("退货数量不能超过本订单实际出库且未退回数量");
                    jdbc.update("UPDATE trade_erp_mock_stock SET quantity=quantity+? WHERE tenant_id=? AND sku_id=?",
                            count, tenant(), item.skuId());
                    jdbc.update("UPDATE trade_erp_mock_order_stock SET returned=returned+? WHERE tenant_id=? AND order_id=? AND sku_id=?",
                            count, tenant(), orderId, item.skuId());
                }
            }
        }
    }

    private int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }

    public Entry reservation(Long orderId) {
        var rows = jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id=? AND operation='RESERVE'",
                this::map, tenant(), orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 历史订单没有 ERP 记录时保留原有支付，不会据此虚构历史出库。 */
    public boolean paymentReady(Long orderId) {
        Entry entry = reservation(orderId);
        return entry == null || entry.confirmed();
    }

    public List<Entry> list(Long orderId) {
        return jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id=? ORDER BY create_time,id",
                this::map, tenant(), orderId);
    }

    public Map<Long,List<Entry>> listByOrderIds(Collection<Long> orderIds) {
        if(orderIds==null || orderIds.isEmpty()) return Map.of();
        if(orderIds.size()>200) throw invalid("每批最多200个订单ERP摘要");
        var args=new ArrayList<Object>();args.add(tenant());args.addAll(orderIds);
        var rows=jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE tenant_id=? AND order_id IN ("
                +String.join(",",Collections.nCopies(orderIds.size(),"?"))+") ORDER BY create_time,id",this::map,args.toArray());
        Map<Long,List<Entry>> grouped=new HashMap<>();for(var row:rows)grouped.computeIfAbsent(row.orderId(),k->new ArrayList<>()).add(row);return grouped;
    }

    public Entry get(String requestId) {
        var rows = jdbc.query("SELECT * FROM trade_erp_inventory_request WHERE id=? AND tenant_id=?", this::map, requestId, tenant());
        if (rows.isEmpty()) throw invalid("ERP 请求不存在或无权访问");
        return rows.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public Entry retry(String requestId) {
        Entry entry = get(requestId);
        lockOrder(entry.orderId());
        entry = get(requestId);
        if (entry.confirmed()) return entry;
        if ("REJECTED".equals(entry.status())) throw invalid("ERP已明确拒绝，不能重置最终结果，请核实业务后另行处理");
        if (!"pending".equals(entry.mode())) throw invalid("模拟请求不能作为正式 ERP 请求重试");
        jdbc.update("UPDATE trade_erp_inventory_request SET status='PENDING',attempts=attempts+1,message=?,erp_no=NULL,"
                + "update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                "已记录重试，沿用原请求号；真实 ERP 尚未接入，仍待确认", requestId, tenant());
        if(workbenchJournal!=null)workbenchJournal.changed(entry.orderId(),cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.Action.ERP_EXCEPTION);
        return get(requestId);
    }

    public void authenticateCallback(String secret) {
        String expected = properties.getCallbackSecret();
        if (!properties.isCallbackEnabled() || expected == null || expected.length() < 32 || secret == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException(403, "ERP 回传未启用或凭证无效");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordCallbackFailure(String requestId, String message) {
        // 由已鉴权控制器在结果事务回滚后调用，不覆盖已经确认/拒绝的最终结果。
        Entry entry=get(requestId);
        jdbc.update("UPDATE trade_erp_inventory_request SET message=?,update_time=CURRENT_TIMESTAMP"
                        + " WHERE id=? AND tenant_id=? AND status='PENDING'",
                message.substring(0, Math.min(message.length(), 500)), requestId, tenant());
        workbenchChanged(entry.orderId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Entry confirm(Callback callback) {
        Entry entry = get(callback.requestId());
        lockOrder(entry.orderId());
        entry = get(callback.requestId());
        if (!"pending".equals(entry.mode())) throw invalid("正式回传不能修改模拟 ERP 请求");
        if (!Set.of("CONFIRMED", "REJECTED").contains(callback.status())
                || callback.erpNo() == null || callback.erpNo().isBlank()
                || callback.message() == null || callback.message().isBlank()) throw invalid("ERP 回传内容无效");
        if (!"PENDING".equals(entry.status())) {
            if (entry.status().equals(callback.status()) && Objects.equals(entry.erpNo(), callback.erpNo())) return entry;
            throw invalid("ERP 请求已有最终结果，不能被重复或矛盾回传覆盖");
        }
        jdbc.update("UPDATE trade_erp_inventory_request SET status=?,erp_no=?,message=?,update_time=CURRENT_TIMESTAMP"
                        + " WHERE id=? AND tenant_id=?", callback.status(), callback.erpNo(), callback.message(), entry.id(), tenant());
        workbenchChanged(entry.orderId());
        return get(entry.id());
    }

    private Entry map(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getString("id"), rs.getLong("order_id"), rs.getString("order_no"), rs.getString("operation"),
                rs.getString("business_key"), rs.getString("mode"), rs.getString("status"), rs.getString("message"),
                rs.getInt("attempts"), rs.getString("erp_no"), rs.getObject("logistics_id", Long.class), rs.getString("logistics_no"),
                JsonUtils.parseArray(rs.getString("payload"), Item.class), rs.getTimestamp("update_time").toLocalDateTime());
    }
}
