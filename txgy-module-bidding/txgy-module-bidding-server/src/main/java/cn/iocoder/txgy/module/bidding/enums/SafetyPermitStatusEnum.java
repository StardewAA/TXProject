package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 安全生产许可证状态（qualification_safety_permit.permit_status 的 code 取值；显式迁移表）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8（VALID/EXPIRING_SOON/EXPIRED/SUSPENDED 暂扣/REVOKED）；
 * 到期状态由 QUAL_EXPIRY 预警扫描联动（≤30 天 VALID→EXPIRING_SOON）；延续走企业资质申报流或平台许可续期编排。
 */
public enum SafetyPermitStatusEnum {

    VALID, EXPIRING_SOON, EXPIRED, SUSPENDED, REVOKED;

    private static final Map<SafetyPermitStatusEnum, Set<SafetyPermitStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(VALID, EXPIRING_SOON, SUSPENDED, REVOKED);
        allow(EXPIRING_SOON, VALID, SUSPENDED, REVOKED, EXPIRED);
        allow(SUSPENDED, VALID, EXPIRING_SOON, REVOKED);
        allow(EXPIRED, SUSPENDED, REVOKED);
        allow(REVOKED);
    }

    private static void allow(SafetyPermitStatusEnum from, SafetyPermitStatusEnum... to) {
        Set<SafetyPermitStatusEnum> set = TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (SafetyPermitStatusEnum t : to) {
            set.add(t);
        }
    }

    public static boolean isTransitionAllowed(SafetyPermitStatusEnum from, SafetyPermitStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
