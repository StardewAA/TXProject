package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 证件/资质生命周期流程状态（cert_lifecycle_record.status 的 code 取值；显式迁移表）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7 的 status 口径（PENDING 待办/REVIEWING
 * 审查中/APPROVED 通过/REJECTED 驳回/COMPLETED·EFFECTIVE 生效），归并为五态；
 * 《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS，非法流转抛错）。
 * <p>为什么这样写：四类流程仅"生效"语义一致但名称不同（续期叫 COMPLETED、变更叫 EFFECTIVE、吊销叫已执行），
 * 统一为 EFFECTIVE 可让"回写主档"这段代码只写一次，避免四套近似分支。
 * <p>业务目标：流程状态不可跳变（如"待办 → 直接生效"必须经过审核），保证审批留痕真实。
 */
public enum CertLifecycleStatusEnum {

    /** 待办（已提交，等待受理/审核） */
    PENDING,
    /** 审核中 */
    REVIEWING,
    /** 审核通过（尚未回写主档） */
    APPROVED,
    /** 驳回（不生效，可重新发起） */
    REJECTED,
    /** 已生效（主档已回写，流程终点） */
    EFFECTIVE;

    private static final Map<CertLifecycleStatusEnum, Set<CertLifecycleStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(PENDING, REVIEWING, APPROVED, REJECTED);
        allow(REVIEWING, APPROVED, REJECTED);
        allow(APPROVED, EFFECTIVE, REJECTED);
        allow(REJECTED);     // 终态：需修改后重新发起新流程
        allow(EFFECTIVE);    // 终态：主档已回写，不允许再次生效
    }

    private static void allow(CertLifecycleStatusEnum from, CertLifecycleStatusEnum... to) {
        Set<CertLifecycleStatusEnum> set = TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (CertLifecycleStatusEnum t : to) {
            set.add(t);
        }
    }

    public static boolean isTransitionAllowed(CertLifecycleStatusEnum from, CertLifecycleStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 是否为终态（不可再流转）。 */
    public static boolean isTerminal(CertLifecycleStatusEnum status) {
        return REJECTED == status || EFFECTIVE == status;
    }
}
