package cn.iocoder.txgy.module.mes.enums.tm;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 工具领用单状态枚举
 */
@Getter
@AllArgsConstructor
public enum MesTmToolIssueStatusEnum implements ArrayValuable<Integer> {

    PREPARE(0, "草稿"),
    CONFIRMED(1, "已确认");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesTmToolIssueStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
