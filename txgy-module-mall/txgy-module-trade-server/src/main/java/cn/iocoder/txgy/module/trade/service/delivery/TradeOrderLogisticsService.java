package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderLogisticsRespVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderLogisticsRespVO.Event;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.enums.ExpressClientEnum;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;
import cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

/** Current-order waybill read model. No inventory, receipt, refund or settlement writes. */
@Service
public class TradeOrderLogisticsService {
    private final TradeOrderMapper orders;
    private final TradeErpInventoryService erp;
    private final DeliveryExpressService carriers;
    private final TradeExpressProperties properties;
    private final ExpressClientFactory clients;
    private final TradeLogisticsQueryCache cache;

    public TradeOrderLogisticsService(TradeOrderMapper orders, TradeErpInventoryService erp,
            DeliveryExpressService carriers, TradeExpressProperties properties,
            ExpressClientFactory clients, TradeLogisticsQueryCache cache) {
        this.orders = orders; this.erp = erp; this.carriers = carriers;
        this.properties = properties; this.clients = clients; this.cache = cache;
    }

    public AppTradeOrderLogisticsRespVO getForBuyer(Long orderId, Long userId) {
        TradeOrderDO order = orders.selectByIdAndUserId(orderId, userId);
        if (order == null || userId == null || !Objects.equals(order.getUserId(), userId)) throw exception(ORDER_NOT_FOUND);
        return getForAuthorizedOrder(order);
    }

    /** Caller must establish buyer or existing staff authorization before this entry point. */
    public AppTradeOrderLogisticsRespVO getForAuthorizedOrder(TradeOrderDO order) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (order == null || tenantId <= 0 || orders.selectCount(new QueryWrapper<TradeOrderDO>()
                .eq("id", order.getId()).eq("tenant_id", tenantId)) != 1L) {
            throw exception(ORDER_NOT_FOUND);
        }
        AppTradeOrderLogisticsRespVO result = new AppTradeOrderLogisticsRespVO()
                .setOrderId(order.getId()).setDeliveryType(order.getDeliveryType()).setTrackingNo(text(order.getLogisticsNo(), 100));
        if (Objects.equals(order.getStatus(), 40)) {
            return state(result, "ORDER_CANCELED", "订单已取消，当前不查询物流");
        }
        List<Entry> entries = erp.list(order.getId());
        Entry outbound = entries.stream().filter(e -> "OUTBOUND".equals(e.operation()))
                .max(Comparator.comparing(Entry::updateTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Entry::id)).orElse(null);
        Long carrierId = order.getLogisticsId();
        if (outbound != null && !outbound.confirmed()) {
            carrierId = outbound.logisticsId();
            result.setTrackingNo(text(outbound.logisticsNo(), 100));
        }
        if (carrierId != null && carrierId > 0) {
            DeliveryExpressDO carrier = carriers.getDeliveryExpress(carrierId);
            if (carrier != null) result.setCarrierName(text(carrier.getName(), 100)).setCarrierCode(text(carrier.getCode(), 100));
        }
        if (Objects.equals(order.getDeliveryType(), 2)) return state(result, "NOT_APPLICABLE", "自提订单无需快递物流");
        if (outbound != null && !outbound.confirmed()) {
            return state(result, "REJECTED".equals(outbound.status()) ? "ERP_REJECTED" : "ERP_PENDING",
                    "REJECTED".equals(outbound.status()) ? "ERP 出库未通过，请联系商家核实" : "待 ERP 确认出库；已登记运单不代表已经发货");
        }
        boolean shipped = Objects.equals(order.getStatus(), 20) || Objects.equals(order.getStatus(), 30);
        if (!shipped) return state(result, "NOT_SHIPPED", "订单尚未发货");
        // New ERP-backed orders cannot use a pre-filled order status to bypass outbound confirmation.
        if (!entries.isEmpty() && outbound == null) return state(result, "ERP_PENDING", "待 ERP 确认出库；当前没有已确认的出库记录");
        if (entries.stream().anyMatch(e -> "mock".equals(e.mode()))) {
            return state(result, "NOT_CONFIGURED", "模拟 ERP 订单不查询真实物流；模拟出库不代表真实运输");
        }
        if (outbound != null && (!Objects.equals(outbound.logisticsId(), order.getLogisticsId())
                || !Objects.equals(outbound.logisticsNo(), order.getLogisticsNo()))) {
            return state(result, "ERP_PENDING", "订单运单与 ERP 确认记录不一致，请联系商家核实");
        }
        if (blank(result.getTrackingNo())) return state(result, "NO_TRACKING", "尚未登记有效快递单号，请联系商家核实");
        if (!configured()) return state(result, "NOT_CONFIGURED", "物流查询暂未开通；已知快递公司和单号可供核实");
        if (blank(result.getCarrierCode())) return state(result, "NO_TRACKING", "快递公司信息不完整，暂时无法查询，请联系商家核实");
        String source = properties.getClient() == ExpressClientEnum.KD_100 ? "kd100" : "kdniao";
        result.setSource(source);
        String identity = tenantId + "|" + order.getId() + "|" + source + "|"
                + result.getCarrierCode() + "|" + result.getTrackingNo() + "|" + Objects.toString(order.getReceiverMobile(), "");
        TradeLogisticsQueryCache.Result queried = cache.query(identity, source,
                result.getCarrierCode().toLowerCase(Locale.ROOT) + "|" + result.getTrackingNo(), () -> {
            ExpressSnapshotRespDTO snapshot = clients.getDefaultExpressClient().getSnapshot(new ExpressTrackQueryReqDTO()
                    .setExpressCode(result.getCarrierCode()).setLogisticsNo(result.getTrackingNo()).setPhone(order.getReceiverMobile()));
            if (snapshot != null && snapshot.getEvents() != null && snapshot.getEvents().size() > 500) {
                throw new IllegalArgumentException("物流事件数量超过安全上限");
            }
            return snapshot;
        });
        result.setCached(queried.isCached()).setRefreshAfterSeconds(queried.getRefreshAfterSeconds()).setUpdatedTime(queried.getQueriedTime());
        if (queried.isFailed() || queried.getSnapshot() == null) return state(result, "PROVIDER_ERROR", "物流查询暂时不可用，请稍后重试；不影响订单实际状态");
        ExpressSnapshotRespDTO snapshot = queried.getSnapshot();
        String status = mapStatus(source, snapshot.getRawStatus());
        result.setRawStatus(text(snapshot.getRawStatus(), 30)).setTransportStatus(status).setTransportStatusLabel(label(status));
        result.setEvents(normalize(snapshot.getEvents(), source));
        return result.getEvents().isEmpty() ? state(result, "NO_EVENTS", "运单已登记，暂未获取物流轨迹；不能据此认定已揽收")
                : state(result, "AVAILABLE", "物流信息由承运服务提供；签收不代替您确认收货");
    }

    private boolean configured() {
        if (!properties.isQueryEnabled()) return false;
        if (properties.getClient() == ExpressClientEnum.KD_100) return properties.getKd100() != null
                && !blank(properties.getKd100().getCustomer()) && !blank(properties.getKd100().getKey());
        if (properties.getClient() == ExpressClientEnum.KD_NIAO) return properties.getKdNiao() != null
                && !blank(properties.getKdNiao().getBusinessId()) && !blank(properties.getKdNiao().getApiKey());
        return false;
    }

    static List<Event> normalize(List<ExpressTrackRespDTO> input, String source) {
        if (input == null) return List.of();
        Map<String, Event> distinct = new LinkedHashMap<>();
        input.stream().filter(Objects::nonNull).forEach(item -> {
            String content = text(item.getContent(), 2000);
            if (blank(content)) return;
            distinct.putIfAbsent(Objects.toString(item.getTime(), "") + "\n" + content,
                    new Event().setTime(item.getTime()).setContent(content).setSource(source));
        });
        return distinct.values().stream().sorted(Comparator.comparing(Event::getTime,
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())).thenComparing(Event::getContent)).toList();
    }

    static String mapStatus(String source, String raw) {
        if (raw == null) return "UNKNOWN";
        if ("kd100".equals(source)) return switch (raw) {
            case "0" -> "IN_TRANSIT"; case "1" -> "PICKED_UP"; case "2", "14" -> "EXCEPTION";
            case "3" -> "SIGNED"; case "4" -> "RETURNED"; case "5" -> "OUT_FOR_DELIVERY"; case "6" -> "RETURNING";
            default -> "UNKNOWN";
        };
        if ("kdniao".equals(source)) return switch (raw) {
            case "1" -> "PICKED_UP"; case "2" -> "IN_TRANSIT"; case "3" -> "SIGNED"; case "4" -> "EXCEPTION";
            default -> "UNKNOWN";
        };
        return "UNKNOWN";
    }

    static String label(String status) {
        return switch (status) {
            case "PICKED_UP" -> "已揽收"; case "IN_TRANSIT" -> "运输中"; case "OUT_FOR_DELIVERY" -> "派送中";
            case "SIGNED" -> "已签收"; case "EXCEPTION" -> "运输异常"; case "RETURNING" -> "退回中";
            case "RETURNED" -> "已退回"; default -> "未知";
        };
    }

    private static AppTradeOrderLogisticsRespVO state(AppTradeOrderLogisticsRespVO result, String status, String message) {
        return result.setQueryStatus(status).setMessage(message);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value, int max) {
        return value == null ? null : value.strip().substring(0, Math.min(value.strip().length(), max));
    }
}
