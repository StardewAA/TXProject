package cn.iocoder.txgy.module.hrm.enums.safety;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 体检结论枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmHealthCheckResultEnum implements ArrayValuable<Integer> {

    QUALIFIED(1, "合格"),
    UNQUALIFIED(2, "不合格"),
    RECHECK(3, "需复查");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmHealthCheckResultEnum::getResult).toArray(Integer[]::new);

    private final Integer result;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmHealthCheckResultEnum valueOf(Integer result) {
        return ArrayUtil.firstMatch(item -> item.getResult().equals(result), values());
    }

}
