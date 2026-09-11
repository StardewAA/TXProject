package cn.iocoder.txgy.module.bidding.enums;

import java.util.Map;
import java.util.Set;

/**
 * 证照台账状态（certificate_info.cert_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二「证照状态口径（依据建办市〔2024〕51号 ZZZT）：
 * ZZZT=正常→ACTIVE；ZZZT=注销→CANCELLED；暂扣/吊销/限期等→SUSPENDED/REVOKED并按字典扩展」；
 * 状态码以 code 存储（《招投标模块开发规范》§5 三-7）。
 * <p>设计遵循/针对：① EXPIRING_SOON/EXPIRED 由定时任务按 valid_to 推导落库（到期预警扫描 v3.3 §八），
 * 人工只可流转状态机定义内的路径；② 自查只认 ACTIVE（v3.3 §二）；③ 相同状态重复提交按幂等 no-op 放行。
 */
public enum CertificateStatusEnum {

    /** 正常（自查只认此状态） */
    ACTIVE,
    /** 即将到期（定时任务推导） */
    EXPIRING_SOON,
    /** 已过期（定时任务推导） */
    EXPIRED,
    /** 暂扣/暂停使用 */
    SUSPENDED,
    /** 吊销 */
    REVOKED,
    /** 注销 */
    CANCELLED;

    /** 允许的状态迁移表（key=当前状态，value=可达状态集合）；设计针对 51号 ZZZT 状态语义，防止非法流转。 */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            ACTIVE.name(), Set.of(EXPIRING_SOON.name(), EXPIRED.name(), SUSPENDED.name(), REVOKED.name(), CANCELLED.name()),
            EXPIRING_SOON.name(), Set.of(ACTIVE.name(), EXPIRED.name(), SUSPENDED.name(), REVOKED.name(), CANCELLED.name()),
            EXPIRED.name(), Set.of(ACTIVE.name(), REVOKED.name(), CANCELLED.name()),
            SUSPENDED.name(), Set.of(ACTIVE.name(), EXPIRED.name(), REVOKED.name(), CANCELLED.name()),
            REVOKED.name(), Set.of(),
            CANCELLED.name(), Set.of());

    /** 判断状态流转是否合法（from == to 视为幂等放行）。 */
    public static boolean isTransitionAllowed(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return true;
        }
        Set<String> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }
}
