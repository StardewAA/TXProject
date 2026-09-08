package cn.iocoder.txgy.module.training.enums.course;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 课程成长等级枚举（对应基础字典 training_growth_level 六级成长体系 course_level）
 * <p>
 * 1 学徒 APPRENTICE / 2 初级工 JUNIOR / 3 中级工 INTERMEDIATE / 4 高级工 SENIOR / 5 技师 TECHNICIAN / 6 高级技师 SENIOR_TECHNICIAN
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum CourseLevelEnum {

    APPRENTICE("APPRENTICE", "学徒", 1),
    JUNIOR("JUNIOR", "初级工", 2),
    INTERMEDIATE("INTERMEDIATE", "中级工", 3),
    SENIOR("SENIOR", "高级工", 4),
    TECHNICIAN("TECHNICIAN", "技师", 5),
    SENIOR_TECHNICIAN("SENIOR_TECHNICIAN", "高级技师", 6);

    private final String level;
    private final String name;
    private final Integer levelNo;

    public static CourseLevelEnum getByLevel(String level) {
        return Arrays.stream(values())
                .filter(item -> item.getLevel().equals(level))
                .findFirst().orElse(null);
    }

}
