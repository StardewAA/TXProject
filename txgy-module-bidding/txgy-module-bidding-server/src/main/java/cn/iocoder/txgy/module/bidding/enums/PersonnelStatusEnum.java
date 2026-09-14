package cn.iocoder.txgy.module.bidding.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 员工在职状态（bidding_personnel_profile.personnel_status 的 code 取值；显式迁移表）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3 `is_full_time`（是否本单位在职=挂靠核查口径）、
 * §十五-要素聚合（"有效在册"人数口径）；《注册建造师管理规定》（建设部令第153号）要求注册人员须受聘于
 * 一个单位——离职/停职人员不得计入资质人员指标，也不得参与投标配证。
 * <p>为什么这样写：状态机必须显式（与 QualificationStatusEnum/SafetyPermitStatusEnum 同款迁移表），
 * 非法直达（如"离职→直接复职跳过停职"）不给走，避免台账被随意改写而失去审计意义。
 * <p>业务目标：人员档案状态是"投标配证/资质申报能用哪些人"的第一道闸门——ACTIVE 才可作为候选，
 * LEAVE/RETIRED 视为不可用，SUSPENDED（停职/待岗）需人工确认后才可再次启用。
 */
public enum PersonnelStatusEnum {

    /** 在职（唯一可作为配证候选的状态） */
    ACTIVE,
    /** 离职（已解除劳动关系，人员证书同步失效） */
    LEAVE,
    /** 停职/待岗（关系存续但不可代表企业投标） */
    SUSPENDED,
    /** 退休 */
    RETIRED;

    private static final Map<PersonnelStatusEnum, Set<PersonnelStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        allow(ACTIVE, LEAVE, SUSPENDED, RETIRED);
        allow(LEAVE, ACTIVE, RETIRED);          // 复职需重新登记；离职后一般不再回岗
        allow(SUSPENDED, ACTIVE, LEAVE, RETIRED);
        allow(RETIRED);                          // 终态
    }

    private static void allow(PersonnelStatusEnum from, PersonnelStatusEnum... to) {
        Set<PersonnelStatusEnum> set = TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>());
        for (PersonnelStatusEnum t : to) {
            set.add(t);
        }
    }

    /** 是否允许 from → to 流转（同态视为无需流转，返回 false，由调用方直接跳过）。 */
    public static boolean isTransitionAllowed(PersonnelStatusEnum from, PersonnelStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 是否可作为投标配证/资质申报的候选人员（仅在职）。 */
    public static boolean isUsable(PersonnelStatusEnum status) {
        return ACTIVE == status;
    }
}
