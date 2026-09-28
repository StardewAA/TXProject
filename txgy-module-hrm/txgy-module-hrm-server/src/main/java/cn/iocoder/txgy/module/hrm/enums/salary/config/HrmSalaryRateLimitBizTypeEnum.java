package cn.iocoder.txgy.module.hrm.enums.salary.config;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 比例上限配置的业务类型枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmSalaryRateLimitBizTypeEnum implements ArrayValuable<Integer> {

    ATTENDANCE_RULE(1, "考勤计薪规则"),
    INSURANCE_SCHEME(2, "参保方案");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmSalaryRateLimitBizTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名称
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmSalaryRateLimitBizTypeEnum valueOf(Integer type) {
        return ArrayUtil.firstMatch(item -> item.getType().equals(type), values());
    }

}
