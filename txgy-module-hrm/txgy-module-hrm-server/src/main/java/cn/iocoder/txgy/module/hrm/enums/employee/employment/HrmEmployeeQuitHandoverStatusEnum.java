package cn.iocoder.txgy.module.hrm.enums.employee.employment;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 员工离职交接状态枚举
 *
 * <p>防腐保温以班组承包为主，退场时工具、劳保用品、证件资料、借支都要有交接结论，
 * 否则"系统里人走了、东西还在他手上"。</p>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeQuitHandoverStatusEnum implements ArrayValuable<Integer> {

    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    CONFIRMED(2, "已确认"),
    WAIVED(3, "无需交接");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmEmployeeQuitHandoverStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static HrmEmployeeQuitHandoverStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

    /**
     * 是否已经拿到交接结论（已确认或无需交接）
     */
    public static boolean isFinished(Integer status) {
        return CONFIRMED.getStatus().equals(status) || WAIVED.getStatus().equals(status);
    }

}
