package cn.iocoder.txgy.module.training.enums.course;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 课程媒资类型枚举
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum CourseMediaTypeEnum {

    DOCUMENT(1, "文档"),
    VIDEO(2, "视频"),
    IMAGE(3, "图片");

    private final Integer type;
    private final String name;

    public static CourseMediaTypeEnum getByType(Integer type) {
        return Arrays.stream(values()).filter(item -> item.getType().equals(type)).findFirst().orElse(null);
    }

}
