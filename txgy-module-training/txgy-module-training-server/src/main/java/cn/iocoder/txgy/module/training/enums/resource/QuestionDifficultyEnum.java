package cn.iocoder.txgy.module.training.enums.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 试题难度枚举
 */
@Getter
@AllArgsConstructor
public enum QuestionDifficultyEnum {

    EASY(1, "易"),
    MEDIUM(2, "中"),
    HARD(3, "难");

    private final Integer difficulty;
    private final String name;
}
