package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 企业资质状态（enterprise_qualification.qual_status 的 code 取值；状态流转走接口，非法流转抛错）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW/
 * LIMITED/REVOKED）；延续/注销等通过表 15-7 申报流办结回写（同事务 + version 乐观锁）。
 * <p>设计遵循/针对：显式迁移表 isTransitionAllowed——延续申报由服务置 PENDING_RENEW，审批通过/退回
 * 分别回写 VALID 或还原；到期状态（EXPIRING_SOON/EXPIRED）由预警扫描联动（M-later 并入 cert_alert 扫描）。
 */
public enum QualificationStatusEnum {

    VALID, EXPIRING_SOON, EXPIRED, PENDING_RENEW, LIMITED, REVOKED;

    private static final Map<QualificationStatusEnum, Set<QualificationStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(VALID, EXPIRING_SOON, PENDING_RENEW, LIMITED, REVOKED);
        allow(EXPIRING_SOON, VALID, PENDING_RENEW, LIMITED, REVOKED, EXPIRED);
        allow(PENDING_RENEW, VALID, LIMITED, REVOKED, EXPIRED);
        allow(LIMITED, VALID, EXPIRING_SOON, REVOKED);
        allow(EXPIRED, PENDING_RENEW, REVOKED); // 过期延续可走申报流
        allow(REVOKED); // 终态
    }

    private static void allow(QualificationStatusEnum from, QualificationStatusEnum... to) {
        TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (QualificationStatusEnum t : to) {
            TRANSITIONS.get(from).add(t);
        }
    }

    /** 是否允许 from → to 流转。 */
    public static boolean isTransitionAllowed(QualificationStatusEnum from, QualificationStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
