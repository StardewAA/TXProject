package cn.iocoder.txgy.module.training.enums.learner;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学员学习任务来源
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum LearnerSourceTypeEnum {

    ASSIGN("assign", "管理员指派"),
    SELF("self", "学员自主选学");

    private final String sourceType;
    private final String name;

    public static LearnerSourceTypeEnum getBySourceType(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        return Arrays.stream(values()).filter(item -> item.getSourceType().equals(sourceType)).findFirst().orElse(null);
    }

}
