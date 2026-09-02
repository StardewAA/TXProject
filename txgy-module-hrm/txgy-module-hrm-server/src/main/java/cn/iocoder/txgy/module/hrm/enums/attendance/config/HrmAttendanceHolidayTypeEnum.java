package cn.iocoder.txgy.module.hrm.enums.attendance.config;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 考勤节假日类型
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmAttendanceHolidayTypeEnum implements ArrayValuable<Integer> {

    WORK(1, "上班"),
    REST(2, "休息");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmAttendanceHolidayTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmAttendanceHolidayTypeEnum valueOf(Integer type) {
        return ArrayUtil.firstMatch(item -> item.getType().equals(type), values());
    }

}
