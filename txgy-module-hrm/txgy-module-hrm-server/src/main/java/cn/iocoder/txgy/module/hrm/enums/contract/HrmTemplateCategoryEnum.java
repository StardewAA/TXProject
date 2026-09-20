package cn.iocoder.txgy.module.hrm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 模板分类枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmTemplateCategoryEnum implements ArrayValuable<Integer> {

    LABOR_CONTRACT(1, "劳动合同"),
    PERSONAL_ARCHIVE(2, "个人电子档案"),
    TRAINING_AGREEMENT(3, "培训协议"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmTemplateCategoryEnum::getCategory).toArray(Integer[]::new);

    /**
     * 分类
     */
    private final Integer category;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmTemplateCategoryEnum valueOf(Integer category) {
        return ArrayUtil.firstMatch(item -> item.getCategory().equals(category), values());
    }

}
