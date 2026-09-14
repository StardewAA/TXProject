package cn.iocoder.txgy.module.wms.service.order;

/** Published after the warehouse execution transaction and its outcome have committed. */
public record WmsWarehouseExecutionEvent(String kind, Long orderId, boolean success, String error) {
}
