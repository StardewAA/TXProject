package cn.iocoder.txgy.module.hrm.enums.employee.employment;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 员工离职交接物品类型枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeQuitHandoverItemTypeEnum implements ArrayValuable<Integer> {

    TOOL(1, "工器具"),
    LABOR_SUPPLY(2, "劳保用品"),
    CERTIFICATE(3, "证件资料"),
    ARCHIVE(4, "档案文件"),
    FINANCE(5, "财务借支"),
    OTHER(6, "其他");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmEmployeeQuitHandoverItemTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmEmployeeQuitHandoverItemTypeEnum valueOf(Integer type) {
        return ArrayUtil.firstMatch(item -> item.getType().equals(type), values());
    }

}
