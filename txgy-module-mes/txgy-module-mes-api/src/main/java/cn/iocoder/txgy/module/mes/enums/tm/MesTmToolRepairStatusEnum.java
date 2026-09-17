package cn.iocoder.txgy.module.mes.enums.tm;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 工具维修单状态枚举
 */
@Getter
@AllArgsConstructor
public enum MesTmToolRepairStatusEnum implements ArrayValuable<Integer> {

    PREPARE(0, "草稿"),
    CONFIRMED(1, "已确认"),
    COMPLETED(2, "维修完成");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesTmToolRepairStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
