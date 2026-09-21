package cn.iocoder.txgy.module.hrm.enums.employment;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 计薪方式枚举
 *
 * <p>防腐保温项目现场点工与包工并存：日结工按工日计酬、计件工按工程量（平米/吨/延米）计酬。</p>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmSalaryModeEnum implements ArrayValuable<Integer> {

    MONTHLY(1, "月薪"),
    DAILY(2, "日薪"),
    PIECE(3, "计件"),
    MIXED(4, "月薪+计件");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmSalaryModeEnum::getMode).toArray(Integer[]::new);

    /**
     * 计薪方式
     */
    private final Integer mode;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmSalaryModeEnum valueOf(Integer mode) {
        return ArrayUtil.firstMatch(item -> item.getMode().equals(mode), values());
    }

}
