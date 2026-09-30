package cn.iocoder.txgy.module.hrm.enums.employee.employment;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 员工异动来源枚举
 *
 * <p>审计用：同一张异动台账里区分"HR 在后台直接办的"、"走审批批下来的"、
 * "定时任务自动生效的"、"再入职带出来的"和"取消离职产生的"。</p>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeChangeSourceEnum implements ArrayValuable<Integer> {

    BACKEND(1, "后台办理"),
    APPROVAL(2, "审批通过"),
    SYSTEM_EFFECT(3, "系统生效"),
    REHIRE(4, "再入职"),
    CANCEL_QUIT(5, "取消离职");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmEmployeeChangeSourceEnum::getSource).toArray(Integer[]::new);

    /**
     * 来源
     */
    private final Integer source;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmEmployeeChangeSourceEnum valueOf(Integer source) {
        return ArrayUtil.firstMatch(item -> item.getSource().equals(source), values());
    }

}
