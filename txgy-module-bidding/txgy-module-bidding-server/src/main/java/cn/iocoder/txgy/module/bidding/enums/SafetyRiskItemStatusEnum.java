package cn.iocoder.txgy.module.bidding.enums;

import java.util.Map;
import java.util.Set;

/**
 * 安许风险条目工单状态（safety_risk_item.status 的 code 取值；五页改造缺接口契约 Q18，2026-09-28）。
 *
 * <p>规范出处：Q18 契约——风险条目可"指派/处理"，状态"形态可议"；取值与**前端已发布类型**对齐：
 * apps/web-antd/src/api/bidding/safetyRisk/index.ts 的 {@code RiskStatus} 与字典 {@code risk_item_status}：
 * PENDING 待处理 / ASSIGNED 已指派 / HANDLING 处理中 / HANDLED 已处理 / IGNORED 已忽略。
 * <p>为什么这样写：契约留了口径，但前端类型与页面兜底文案已按上表落地（含"已忽略"与"已指派/处理中"分离）；
 * 后端单向对齐可避免前端改名/改类型，且字典 code 与枚举 name 同源（dict risk_item_status 一一对应）。
 * <p>流转：PENDING→ASSIGNED/HANDLING/HANDLED/IGNORED；ASSIGNED→HANDLING/HANDLED/IGNORED；
 * HANDLING→HANDLED/IGNORED；HANDLED/IGNORED 为终态（不复活旧单：风险仍在时由重新扫描新开工单）。
 */
public enum SafetyRiskItemStatusEnum {

    /** 待处理（扫描生成，尚未指派） */
    PENDING,
    /** 已指派（已指定责任人，尚未开始处理） */
    ASSIGNED,
    /** 处理中 */
    HANDLING,
    /** 已处理（终态） */
    HANDLED,
    /** 已忽略（终态：经确认无需处理） */
    IGNORED;

    /** 未闭环状态（扫描去重 / 自动闭环 / 待办口径用）；HANDLED、IGNORED 视为闭环。 */
    public static final Set<String> OPEN_STATES = Set.of(PENDING.name(), ASSIGNED.name(), HANDLING.name());

    /** 允许的状态迁移表（key=当前状态，value=可达状态）；终态不在表中（不可迁移）。 */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            PENDING.name(), Set.of(ASSIGNED.name(), HANDLING.name(), HANDLED.name(), IGNORED.name()),
            ASSIGNED.name(), Set.of(HANDLING.name(), HANDLED.name(), IGNORED.name()),
            HANDLING.name(), Set.of(HANDLED.name(), IGNORED.name()));

    /**
     * 是否允许 from→to 迁移；同值视为幂等 no-op 放行（前端重复提交不报错）。
     */
    public static boolean canTransit(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** 是否终态（HANDLED/IGNORED）：终态不允许再指派/处理。 */
    public static boolean isTerminal(String status) {
        return HANDLED.name().equals(status) || IGNORED.name().equals(status);
    }

    /** 状态码是否在枚举值域内（防前端/字典脏码落库）。 */
    public static boolean isKnown(String status) {
        for (SafetyRiskItemStatusEnum item : values()) {
            if (item.name().equals(status)) {
                return true;
            }
        }
        return false;
    }
}
