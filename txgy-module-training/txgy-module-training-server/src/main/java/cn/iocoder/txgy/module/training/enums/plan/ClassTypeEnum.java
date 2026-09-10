package cn.iocoder.txgy.module.training.enums.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 培训班培训类型枚举
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum ClassTypeEnum {

    PRE_JOB(1, "岗前培训"),
    RETURN_WORK(2, "复工培训"),
    SKILL(3, "工人技能培训"),
    QUALITY(4, "质检专项"),
    SAFETY(5, "安全管理"),
    SPECIAL_CERT(6, "特种作业持证"),
    APPRENTICE(7, "学徒进阶培训班"),
    COMPOSITE(8, "复合型人才专项培训班");

    private final Integer type;
    private final String name;

    public static ClassTypeEnum getByType(Integer type) {
        return Arrays.stream(values()).filter(item -> item.getType().equals(type)).findFirst().orElse(null);
    }

    public static String getName(Integer type) {
        ClassTypeEnum item = getByType(type);
        return item == null ? String.valueOf(type) : item.getName();
    }

}
