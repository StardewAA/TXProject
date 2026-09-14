package cn.iocoder.txgy.module.bidding.enums;

import java.util.Map;
import java.util.Set;

/**
 * 安许 A/B/C 企业级风险档（qualification_safety_permit.risk_flag 的 code 取值）。
 *
 * <p>规范出处：用户业务口径"A/B/C 任一人员数量不达标→黄色；任一为 0→红色；全为 0→失效；一年预警 ≥4 次→频繁"；
 * 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-1（陕西 2026-03-26 起三类安管人员配备动态核查）；
 * 《电子资质七大模块技术方案.md》§1.2（判定顺序：风险与非日期型失效必须排在日期判断之前）。
 * <p>为什么档位要分五值而不是"正常/异常"两值：五个档位对应**不同的处置动作**——失效与红色阻断投标、
 * 黄色仅提示并限期整改、频繁是趋势性标签（提醒监管部门关注）而非当下不合格；混成一个"异常"会让
 * 处置动作无从选择，也会让企业无法区分"必须马上办"和"该注意了"。
 * <p>业务目标：招投标、施工许可、现场监督检查读同一档位，形成一致口径。
 */
public enum SafetyRiskFlagEnum {

    /** 正常（三类配备均达标） */
    NORMAL,
    /** 黄色预警（任一 required 类低于配备要求） */
    YELLOW,
    /** 红色预警（任一 required 类为 0，豁免类型除外） */
    RED,
    /** 失效预警（三类全为 0；逾期不整改列失效） */
    INVALID,
    /** 频繁预警（窗口内预警次数达阈值，趋势性标签） */
    FREQUENT;

    /**
     * 严重度排序（数值越大越严重），用于"宽限降一档"与"多条件命中取最严重"。
     *
     * <p>为什么不用枚举声明顺序：声明顺序属于实现细节，未来插入新档位会静默改变严重度语义；
     * 显式映射可以避免这种"加个枚举值就把判定逻辑改坏"的事故。
     */
    private static final Map<String, Integer> SEVERITY = Map.of(
            NORMAL.name(), 0,
            FREQUENT.name(), 1,
            YELLOW.name(), 2,
            RED.name(), 3,
            INVALID.name(), 4);

    /** 严重度值（未知档位按 0 处理，保证判定不因脏数据抛错）。 */
    public static int severity(String flag) {
        return SEVERITY.getOrDefault(flag, 0);
    }

    /** 取较严重的一方（多条件命中时使用）。 */
    public static String moreSevere(String left, String right) {
        return severity(left) >= severity(right) ? left : right;
    }

    /**
     * 降一档（宽限期用：RED→YELLOW、YELLOW→NORMAL、INVALID→RED、FREQUENT 不变）。
     *
     * <p>为什么 INVALID 只降到 RED 而不是直接 NORMAL：三类人员全为 0 属最严重情形，
     * 即便给了整改宽限，也不应视同"正常"，仍须阻断高风险业务。
     */
    public static String downgrade(String flag) {
        if (flag == null) {
            return NORMAL.name();
        }
        return switch (flag) {
            case "INVALID" -> RED.name();
            case "RED" -> YELLOW.name();
            case "YELLOW" -> NORMAL.name();
            default -> flag;
        };
    }

    /** 是否为阻断档（实际阻断集合由配置 safety.abc.block.flags 决定，此处仅提供默认判断）。 */
    public static boolean isBlockingByDefault(String flag) {
        return Set.of(INVALID.name(), RED.name()).contains(flag);
    }
}
