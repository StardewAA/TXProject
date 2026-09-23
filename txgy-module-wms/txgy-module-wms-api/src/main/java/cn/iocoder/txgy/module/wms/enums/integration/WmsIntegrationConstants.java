package cn.iocoder.txgy.module.wms.enums.integration;

public final class WmsIntegrationConstants {

    private WmsIntegrationConstants() {
    }

    public static final String SOURCE_ERP = "ERP";
    public static final String SOURCE_MALL = "MALL";
    public static final String SOURCE_WMS = "WMS";

    public static final String MATERIAL_RAW = "RAW_MATERIAL";
    public static final String MATERIAL_FINISHED = "FINISHED_GOODS";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_FULL = "FULL";
    public static final String DECISION_PARTIAL = "PARTIAL";
    public static final String DECISION_SHORTAGE = "SHORTAGE";
    public static final String DECISION_REJECTED = "REJECTED";

    public static final String RESERVATION_NONE = "NONE";
    public static final String RESERVATION_PARTIAL = "PARTIAL";
    public static final String RESERVATION_FULL = "FULL";
    public static final String RESERVATION_CONSUMED = "CONSUMED";
    public static final String RESERVATION_RELEASING = "RELEASING";
    public static final String RESERVATION_RELEASED = "RELEASED";

}
