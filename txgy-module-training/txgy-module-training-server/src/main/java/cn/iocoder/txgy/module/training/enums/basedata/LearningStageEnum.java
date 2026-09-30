package cn.iocoder.txgy.module.training.enums.basedata;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 学习地图成长阶段枚举
 *
 * 对应《云培训学院-学习地图示例.xlsx》的阶段列：
 * entry 入门段(L1→L2) / core 核心段(L2→L3) / advanced 进阶段(L3→L4) / technician 技师段(L4→L5)
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum LearningStageEnum {

    ENTRY("entry", "入门段(L1→L2)", 1),
    CORE("core", "核心段(L2→L3)", 2),
    ADVANCED("advanced", "进阶段(L3→L4)", 3),
    TECHNICIAN("technician", "技师段(L4→L5)", 4);

    /**
     * 阶段编码
     */
    private final String stage;
    /**
     * 阶段名称
     */
    private final String name;
    /**
     * 阶段序号
     */
    private final Integer no;

    public static LearningStageEnum of(String stage) {
        for (LearningStageEnum item : values()) {
            if (item.stage.equalsIgnoreCase(stage)) {
                return item;
            }
        }
        return null;
    }

    public static String getName(String stage) {
        LearningStageEnum item = of(stage);
        return item != null ? item.getName() : null;
    }

    public static Integer getNo(String stage) {
        LearningStageEnum item = of(stage);
        return item != null ? item.getNo() : null;
    }

}
