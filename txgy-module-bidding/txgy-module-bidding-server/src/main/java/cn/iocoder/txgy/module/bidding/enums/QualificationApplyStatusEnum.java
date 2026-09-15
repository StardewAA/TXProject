package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 资质业务申报状态（qualification_apply_record.status 的 code 取值；状态机 + 乐观锁）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7
 * （DRAFT/SUBMITTED/ACCEPTED 受理/REVIEWING 审核/APPROVED/PUBLISHED 公示/ISSUED 领证/REJECTED 退回）。
 */
public enum QualificationApplyStatusEnum {

    DRAFT, SUBMITTED, ACCEPTED, REVIEWING, APPROVED, PUBLISHED, ISSUED, REJECTED;

    private static final Map<QualificationApplyStatusEnum, Set<QualificationApplyStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(DRAFT, SUBMITTED);
        allow(SUBMITTED, ACCEPTED, REVIEWING, APPROVED, REJECTED);
        allow(ACCEPTED, REVIEWING, REJECTED);
        allow(REVIEWING, APPROVED, REJECTED);
        allow(APPROVED, PUBLISHED, ISSUED);
        allow(PUBLISHED, ISSUED);
        allow(ISSUED);
        allow(REJECTED); // 退回可重新发起新申报（DRAFT）
    }

    private static void allow(QualificationApplyStatusEnum from, QualificationApplyStatusEnum... to) {
        Set<QualificationApplyStatusEnum> set = TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (QualificationApplyStatusEnum t : to) {
            set.add(t);
        }
    }

    public static boolean isTransitionAllowed(QualificationApplyStatusEnum from, QualificationApplyStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
