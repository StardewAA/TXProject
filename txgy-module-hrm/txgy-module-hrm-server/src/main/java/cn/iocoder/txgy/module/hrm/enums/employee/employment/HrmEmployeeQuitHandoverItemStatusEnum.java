package cn.iocoder.txgy.module.hrm.enums.employee.employment;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 员工离职交接明细状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeQuitHandoverItemStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待交回"),
    RETURNED(1, "已交回"),
    LOST(2, "缺失未交回"),
    WAIVED(3, "无需交接");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmEmployeeQuitHandoverItemStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmEmployeeQuitHandoverItemStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
