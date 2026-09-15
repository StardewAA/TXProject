package cn.iocoder.txgy.module.bidding.enums;

/**
 * 项目关键岗位（project_key_post_assignment.post_code 的 code 取值）。
 *
 * <p>规范出处：用户业务口径"增加对同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；
 * 《电子资质七大模块技术方案.md》§1.1(e)/§1.3（关键岗位变更统计与阈值）。
 * <p>为什么把岗位做成枚举而不是自由文本：频繁变更的统计口径依赖"同一岗位"的确定性判定，
 * 自由文本会出现"项目经理/项目负责人/项目经理(执行)"被当成三个岗位，统计随之失真。
 * <p>业务目标：关键岗位换人可量化，挂靠与项目管理失控可被及时发现。
 */
public enum KeyPostEnum {

    /** 项目经理（注册建造师） */
    PROJECT_MANAGER("项目经理"),
    /** 技术负责人 */
    TECH_LEADER("技术负责人"),
    /** 专职安全生产管理人员 */
    SAFETY_OFFICER("专职安全员"),
    /** 质量负责人 */
    QUALITY_OFFICER("质量负责人"),
    /** 施工员 */
    CONSTRUCTION_ENGINEER("施工员");

    private final String label;

    KeyPostEnum(String label) {
        this.label = label;
    }

    /** 中文名（提示文案与留痕使用）。 */
    public String getLabel() {
        return label;
    }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (KeyPostEnum item : values()) {
            if (item.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 取中文名，未知 code 回落原值（不因脏数据报错）。 */
    public static String labelOf(String code) {
        for (KeyPostEnum item : values()) {
            if (item.name().equals(code)) {
                return item.getLabel();
            }
        }
        return code;
    }
}
