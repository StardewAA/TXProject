package cn.iocoder.txgy.module.training.enums.course;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 课程成长等级枚举（对应资源中心四级成长体系 course_level）
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum CourseLevelEnum {

    APPRENTICE("APPRENTICE", "新手"),
    JUNIOR("JUNIOR", "成长"),
    MIDDLE("MIDDLE", "骨干"),
    SENIOR("SENIOR", "资深");

    private final String level;
    private final String name;

    public static CourseLevelEnum getByLevel(String level) {
        return Arrays.stream(values())
                .filter(item -> item.getLevel().equals(level))
                .findFirst().orElse(null);
    }

}
