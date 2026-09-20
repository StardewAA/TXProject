package cn.iocoder.txgy.module.erp.enums;

/**
 * ERP 供应商资信控制常量。
 *
 * <p>v0.1 只落地业务状态和采购拦截基线，评分权重与等级阈值待后续版本配置。</p>
 */
public final class ErpSupplierCreditConstants {

    private ErpSupplierCreditConstants() {
    }

    public static final String AUTH_DRAFT = "DRAFT";
    public static final String AUTH_PENDING = "PENDING";
    public static final String AUTH_APPROVED = "APPROVED";
    public static final String AUTH_REJECTED = "REJECTED";
    public static final String AUTH_NORMAL = "NORMAL";
    public static final String AUTH_FROZEN = "FROZEN";
    public static final String AUTH_CANCELLED = "CANCELLED";

    public static final String LIMIT_NONE = "NONE";
    public static final String LIMIT_WARNING = "WARNING";
    public static final String LIMIT_LIMITED = "LIMITED";
    public static final String LIMIT_FROZEN = "FROZEN";
    public static final String LIMIT_BLACKLISTED = "BLACKLISTED";

    public static final String QUAL_VALID = "VALID";
    public static final String QUAL_EXPIRING_SOON = "EXPIRING_SOON";
    public static final String QUAL_EXPIRED = "EXPIRED";
    public static final String QUAL_REVOKED = "REVOKED";
    public static final String QUAL_PENDING_REVIEW = "PENDING_REVIEW";

    public static final String RISK_DRAFT = "DRAFT";
    public static final String RISK_PENDING = "PENDING";
    public static final String RISK_CONFIRMED = "CONFIRMED";
    public static final String RISK_REJECTED = "REJECTED";
    public static final String RISK_PROCESSING = "PROCESSING";
    public static final String RISK_CLOSED = "CLOSED";

    public static final String EVENT_QUALIFICATION_EXPIRED = "QUALIFICATION_EXPIRED";
    public static final String EVENT_DELIVERY_DELAY = "DELIVERY_DELAY";
    public static final String EVENT_QUALITY_FAILURE = "QUALITY_FAILURE";
    public static final String EVENT_MAJOR_RETURN = "MAJOR_RETURN";
    public static final String EVENT_PRICE_EXCEPTION = "PRICE_EXCEPTION";
    public static final String EVENT_SETTLEMENT_DISPUTE = "SETTLEMENT_DISPUTE";
    public static final String EVENT_CONTRACT_BREACH = "CONTRACT_BREACH";
    public static final String EVENT_MANUAL_WARNING = "MANUAL_WARNING";
    public static final String EVENT_OTHER = "OTHER";
}
