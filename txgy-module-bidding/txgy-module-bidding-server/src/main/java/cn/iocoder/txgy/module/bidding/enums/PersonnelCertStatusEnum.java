package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 员工证书台账状态（personnel_certificate.cert_status 的 code 取值；显式迁移表）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（证照状态口径对齐建办市〔2024〕51号 ZZZT：
 * 正常=ACTIVE、注销=CANCELLED、暂扣/吊销=SUSPENDED/REVOKED）、§九（到期预警把 ACTIVE 推进 EXPIRING_SOON/EXPIRED）。
 * <p>为什么这样写：人员证书与企业证照共用同一套状态口径（前台展示与预警口径一致），但人员证书多一类
 * "注册失效"现实（注册单位变更/注销注册），用 REVOKED 承载并在合规核验中按"不可用"处理。
 * <p>业务目标：投标配证与资质申报只认 VALID/EXPIRING_SOON 的证书，过期/吊销证书自动被排除，防止
 * 用失效证书投标导致废标。
 */
public enum PersonnelCertStatusEnum {

    /** 有效 */
    VALID,
    /** 即将到期（预警扫描 ≤30 天自动置位） */
    EXPIRING_SOON,
    /** 已过期 */
    EXPIRED,
    /**
     * 暂扣/暂停（年检未通过、主管部门暂扣、注册暂停）。
     *
     * <p>规范出处：06 文档 §二-1/§二-4（安许证动态核查"3 个月不整改列失效"、复审逾期证书被暂停/撤销）；
     * v3.3 §二 状态口径（暂扣=SUSPENDED）。
     * <p>为什么单独一类：暂停是"可恢复的失效"——与吊销（REVOKED）/注销（CANCELLED）这类终态不同，
     * 整改合格后可恢复为 VALID；混为一谈会让"整改后能否再用"无法表达。
     */
    SUSPENDED,
    /** 吊销/注销注册 */
    REVOKED,
    /** 注销（持证人申请注销） */
    CANCELLED;

    private static final Map<PersonnelCertStatusEnum, Set<PersonnelCertStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(VALID, EXPIRING_SOON, EXPIRED, SUSPENDED, REVOKED, CANCELLED);
        allow(EXPIRING_SOON, VALID, EXPIRED, SUSPENDED, REVOKED, CANCELLED);
        allow(EXPIRED, VALID, SUSPENDED, REVOKED, CANCELLED);   // 换证后重新登记为 VALID
        allow(SUSPENDED, VALID, EXPIRED, REVOKED, CANCELLED);   // 整改合格可恢复，逾期则吊销/注销
        allow(REVOKED);
        allow(CANCELLED);
    }

    private static void allow(PersonnelCertStatusEnum from, PersonnelCertStatusEnum... to) {
        Set<PersonnelCertStatusEnum> set = TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (PersonnelCertStatusEnum t : to) {
            set.add(t);
        }
    }

    public static boolean isTransitionAllowed(PersonnelCertStatusEnum from, PersonnelCertStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 是否可用于投标配证/资质申报（有效或临近到期，过期/吊销/注销一律不可用）。 */
    public static boolean isUsable(String code) {
        return VALID.name().equals(code) || EXPIRING_SOON.name().equals(code);
    }
}
