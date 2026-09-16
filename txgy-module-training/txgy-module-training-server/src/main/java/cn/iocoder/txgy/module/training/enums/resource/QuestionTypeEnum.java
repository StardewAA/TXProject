package cn.iocoder.txgy.module.training.enums.resource;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 试题题型枚举
 */
@Getter
@AllArgsConstructor
public enum QuestionTypeEnum {

    SINGLE(1, "single", "单选题"),
    MULTIPLE(2, "multiple", "多选题"),
    JUDGE(3, "judge", "判断题"),
    QA(4, "qa", "实操问答"),
    CASE(5, "case", "案例分析"),
    FILL_BLANK(6, "fill_blank", "填空题"),
    SHORT_ANSWER(7, "short_answer", "简答题");

    private final Integer type;
    private final String code;
    private final String name;

    public static QuestionTypeEnum fromType(Integer type) {
        return ArrayUtil.firstMatch(item -> item.getType().equals(type), values());
    }

    public static QuestionTypeEnum fromCode(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }
}
